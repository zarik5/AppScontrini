package com.ing.software.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.support.annotation.NonNull;
import android.util.Pair;

import com.ing.software.common.Scored;
import com.ing.software.ocr.OcrObjects.OcrText;
import com.ing.software.ocr.OcrObjects.TicketSchemes.*;
import com.ing.software.ocr.OperativeObjects.AmountComparator;
import com.ing.software.ocr.OperativeObjects.ListAmountOrganizer;
import com.ing.software.ocr.OperativeObjects.RawImage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import com.annimon.stream.function.Consumer;

import com.annimon.stream.Stream;
import com.ing.software.ocr.OperativeObjects.ScoreFunc;

import static com.ing.software.ocr.OcrUtils.IS_DEBUG_ENABLED;
import static java.util.Arrays.asList;
import static com.ing.software.common.CommonUtils.*;
import static com.ing.software.ocr.DataAnalyzer.*;
import static com.ing.software.ocr.OcrOptions.*;

/**
 * Class to control ocr analysis
 * <p> This class is thread-safe. </p>
 *
 * <p>USAGE:</p>
 * <ol> Instantiate {@link OcrManager}; </ol>
 * <ol> Call {@link #initialize(Context)} until it returns 0;</ol>
 * <ol> Call {@link #getTicket(ImageProcessor, OcrOptions)} ad libitum to extract an {@link OcrTicket} from a photo of a ticket.</ol>
 * <ol> Call {@link #release()} to release internal resources.</ol>
 *
 * <p> For a code usage example follow this link: https://github.com/IngSoftware2017/AppScontrini/wiki/OCR </p>
 */
public class OcrManager {

    /*
     @author Zaglia
     */
    private static final double MIN_LANGUAGE_SCORE = 10;

    //todo: use a more general function
    private static final Map<Locale, Locale> LANGUAGE_TO_COUNTRY = new HashMap<>();
    private static final Map<Locale, Locale> COUNTRY_TO_LANGUAGE = new HashMap<>();

    static {
        LANGUAGE_TO_COUNTRY.put(Locale.ITALIAN, Locale.ITALY);
        LANGUAGE_TO_COUNTRY.put(Locale.ENGLISH, Locale.UK);
        //adding US would overwrite UK.

        COUNTRY_TO_LANGUAGE.put(Locale.ITALY, Locale.ITALIAN);
        COUNTRY_TO_LANGUAGE.put(Locale.UK, Locale.ENGLISH);
        COUNTRY_TO_LANGUAGE.put(Locale.US, Locale.ENGLISH);
    }


    private final OcrAnalyzer analyzer = new OcrAnalyzer();
    private boolean operative = false;

    /**
     * Initialize OcrAnalyzer
     *
     * @param context Android context
     * @return 0 if everything ok, negative number if an error occurred
     */
    public synchronized int initialize(@NonNull Context context) {
        OcrUtils.log(1, "OcrManager", "Initializing OcrManager");
        int r = analyzer.initialize(context);
        operative = (r == 0);
        return r;
    }

    /**
     * Release internal resources
     */
    public synchronized void release() {
        operative = false;
        analyzer.release();
    }

    /**
     * Single run ocr analysis.
     * @param ticket in-out ticket. Not null.
     * @param procCopy image processor. Not modified. Not null.
     * @param options ocr options. Not modified. Not null.
     *
     * @author Luca Michelon
     * @author Riccardo Zaglia
     */
    private void extractTicket(@NonNull OcrTicket ticket, @NonNull ImageProcessor procCopy, @NonNull OcrOptions options) {
        long startTime;
        long endTime;
        if (IS_DEBUG_ENABLED)
            startTime = System.nanoTime();
        Bitmap frame = procCopy.undistortForOCR(options.getResolutionMultiplier());
        if (frame == null) {
            ticket.errors.add(OcrError.INVALID_PROCESSOR);
            return;
        }
        ticket.rectangle = procCopy.getCorners();

        if (IS_DEBUG_ENABLED) {
            endTime = System.nanoTime();
            double duration = ((double) (endTime - startTime)) / 1000000000;
            OcrUtils.log(1, "EXECUTION TIME: ", "Processor: " + duration + " seconds");
        }

        ticket.errors = new HashSet<>();
        if (IS_DEBUG_ENABLED)
            startTime = System.nanoTime();

        RawImage mainImage = new RawImage(frame);
        analyzer.setMainImage(mainImage);
        OcrUtils.log(1, "MANAGER: ", "Starting image analysis...");
        List<OcrText> texts = analyzer.analyze(frame); //Get main texts
        mainImage.setLines(texts); //save rect configuration in rawimage and Prepare scheme of the ticket
        if (IS_DEBUG_ENABLED)
            OcrUtils.listEverything(mainImage);

        Locale country = null, language = null;
        double languageScore = 0;
        if (options.suggestedCountry != null
                && COUNTRY_TO_LANGUAGE.keySet().contains(options.suggestedCountry)) {
            country = options.suggestedCountry;
            language = COUNTRY_TO_LANGUAGE.get(country);
        }

        { // if found, currency is a great estimator for the country locale
            Locale currencyCountry = getCurrencyCountry(texts);
            if (currencyCountry != null) {
                country = currencyCountry;
            }
        }

        OcrTicket newTicket = new OcrTicket();

        BigDecimal restoredAmount = null; //contains modified amount
        OcrText amountPriceText = null; //text containing price for total
        Scored<TicketScheme> bestTicketScheme = null; //ticket with highest score for amount (contains list of prices etc.)
        Scored<TicketScheme> bestRestoredTicketScheme = null;
        boolean editTotal = options.priceEditing.ordinal() >= PriceEditing.ALLOW_STRICT.ordinal();
        if (options.totalSearch != TotalSearch.SKIP) {
            /*
            Steps:
            1- search string in texts --> Data Analyzer --> Word Matcher
            2- detect language according to texts --> DataAnalyzer
            3- add score to chosen texts --> Score Func
            4- if enabled redo ocr on source strip (take old prices rect and overwrite old texts) --> OcrAnalyzer
            5- add score to target texts --> ListAmountOrganizer --> Score Func
            6- decode amount from target texts --> Data Analyzer --> Word Matcher
            7- decode restored amount from target texts --> Data Analyzer
            8- if enabled redo ocr on prices strip and update mainImage (take old prices rect and overwrite old texts) --> OcrAnalyzer todo
            9- check amount and restored amount against cash and products prices --> Amount Comparator
            10- set amount retrieving best ticket from amount comparator
             */

            //Redo ocr on prices strip if enabled (must do it here to avoid overwriting total price, which has an optimized aspect ratio) //8
            if (options.productsSearch == ProductsSearch.DEEP) {
                RectF extendedRect = OcrUtils.extendRect(mainImage.getPricesRect(), 5, 10);
                List<Scored<OcrText>> pricesTexts = analyzer.getTextsInStrip(procCopy, OcrUtils.imageToSize(mainImage), extendedRect);
                mainImage.replaceTexts(pricesTexts, extendedRect);
            }

            List<Scored<Pair<OcrText, Locale>>> amountStringsWithLocale = DataAnalyzer.findAmountStringTexts(texts); //1
            List<Scored<Pair<OcrText, Locale>>> subtotalStringsWithLocale = DataAnalyzer.findSubtotalStringTexts(texts); //1
            List<Scored<Pair<OcrText, Locale>>> cashStringsWithLocale = DataAnalyzer.findCashStringTexts(texts); //1
            List<Scored<Pair<OcrText, Locale>>> changeStringsWithLocale = DataAnalyzer.findChangeStringTexts(texts); //1
            List<Scored<Pair<OcrText, Locale>>> coverStringsWithLocale = DataAnalyzer.findCoverStringTexts(texts); //1
            List<Scored<Pair<OcrText, Locale>>> taxStringsWithLocale = DataAnalyzer.findTaxStringTexts(texts); //1

            Scored<Locale> scoredLanguage = DataAnalyzer.getBestLanguage(asList(amountStringsWithLocale,
                    subtotalStringsWithLocale, cashStringsWithLocale, changeStringsWithLocale,
                    coverStringsWithLocale, taxStringsWithLocale)); //2
            language = scoredLanguage.obj();
            languageScore = scoredLanguage.getScore();

            if (language != null) {

                List<Scored<OcrText>> amountStrings = DataAnalyzer.filterForLanguage(amountStringsWithLocale, language);
                List<Scored<OcrText>> subtotalStrings = DataAnalyzer.filterForLanguage(subtotalStringsWithLocale, language);
                List<Scored<OcrText>> cashStrings = DataAnalyzer.filterForLanguage(cashStringsWithLocale, language);
                List<Scored<OcrText>> changeStrings = DataAnalyzer.filterForLanguage(changeStringsWithLocale, language);
                List<Scored<OcrText>> coverStrings = DataAnalyzer.filterForLanguage(coverStringsWithLocale, language);
                List<Scored<OcrText>> taxStrings = DataAnalyzer.filterForLanguage(taxStringsWithLocale, language);

                List<ListAmountOrganizer> amountList = DataAnalyzer.organizeAmountList(amountStrings, mainImage); //3
                Collections.sort(amountList, Collections.reverseOrder()); //sort from highest to lowest score
                if (!amountList.isEmpty()) {
                    List<Scored<OcrText>> amountPrices;
                    int i = 0;
                    int maxScan = (options.totalSearch == TotalSearch.EXTENDED_SEARCH ? 3 : 1); //number of scans allowed
                    while (i < amountList.size()) { //This goes on until I have a valid total (not restored). First restored total is not overwritten in the loop
                        OcrText amountStringText = amountList.get(i).getSourceText().obj();
                        RectF stripBoundingBox = OcrAnalyzer.getAmountExtendedBox(amountStringText, mainImage.getWidth()); //get rect optimized to find amount
                        if (options.totalSearch.ordinal() >= TotalSearch.DEEP.ordinal() && i <= maxScan) {
                            //Redo ocr on i element of list.
                            amountPrices = analyzer.getTextsInStrip(procCopy, OcrUtils.imageToSize(mainImage), amountStringText, stripBoundingBox);//4
                            mainImage.replaceTexts(amountPrices, stripBoundingBox);
                        } else {
                            //Search total price using current texts
                            amountPrices = analyzer.getOrigTexts(amountStringText, stripBoundingBox);
                        }
                        //set target texts (prices) to source text (containing total string)
                        amountList.get(i).setAmountTargetTexts(amountPrices); //5
                        AmountComparator amountComparator;
                        Pair<OcrText, BigDecimal> amountT = DataAnalyzer.getMatchingAmount(amountList.get(i).getTargetTexts(), false); //6
                        if (amountT != null && newTicket.total == null) {
                            newTicket.total = amountT.second; //BigDecimal containing total price
                            amountPriceText = amountT.first; //Text containing total price
                            //Initialize amount comparator with specific schemes if 'contante'/'resto'/'subtotale' are found
                            List<TicketScheme> schemes = new ArrayList<>();
                            if (!subtotalStrings.isEmpty() || !changeStrings.isEmpty()) { // may miss one of the two, accept anyway
                                schemes.add(new TicketSchemeIT_PSCC(new Pair<>(amountT.first, newTicket.total),
                                        AmountComparator.getAboveTotalPrices(amountT.first, mainImage), AmountComparator.getBelowTotalPrices(amountT.first, mainImage)));
                                OcrUtils.log(2, "MAN.AmountComparator", "Adding scheme: PSCC");
                            }
                            if (!subtotalStrings.isEmpty()) {
                                schemes.add(new TicketSchemeIT_PSC(new Pair<>(amountT.first, newTicket.total),
                                        AmountComparator.getAboveTotalPrices(amountT.first, mainImage), AmountComparator.getBelowTotalPrices(amountT.first, mainImage)));
                                OcrUtils.log(2, "MAN.AmountComparator", "Adding scheme: PSC");
                            }
                            if (!changeStrings.isEmpty()) {
                                schemes.add(new TicketSchemeIT_PCC(new Pair<>(amountT.first, newTicket.total),
                                        AmountComparator.getAboveTotalPrices(amountT.first, mainImage), AmountComparator.getBelowTotalPrices(amountT.first, mainImage)));
                                OcrUtils.log(2, "MAN.AmountComparator", "Adding scheme: PCC");
                            }
                            if (!schemes.isEmpty()) {
                                OcrUtils.log(2, "", "Using custom amount comparator");
                                amountComparator = new AmountComparator(schemes);
                            } else {
                                OcrUtils.log(2, "", "Using default amount comparator");
                                amountComparator = new AmountComparator(newTicket.total, amountT.first, mainImage); //9
                            }
                            bestTicketScheme = amountComparator.getBestAmount(!editTotal); //editTotal == false => total is not overwritten, only scheme with a complete
                            if (bestTicketScheme != null) {
                                OcrUtils.log(2, "MANAGER.Comparator", "Best amount is: " + bestTicketScheme.obj().getBestAmount().second.setScale(2, RoundingMode.HALF_UP) +
                                        "\nwith scheme: " + bestTicketScheme.obj().toString() + "\nand score: " + bestTicketScheme.getScore());
                            } else {
                                OcrUtils.log(2, "MANAGER.Comparator", "Best amount is: " + newTicket.total.setScale(2, RoundingMode.HALF_UP));
                            }
                        }
                        if (editTotal) {
                            //use different method to decode a price, resulting price may be modified
                            Pair<OcrText, BigDecimal> restoredAmountT = DataAnalyzer.getRestoredAmount(amountList.get(i).getTargetTexts()); //7
                            if (restoredAmountT != null && bestRestoredTicketScheme == null) { //Do not find a new restored amount if one is already saved
                                restoredAmount = restoredAmountT.second; //BigDecimal containing total price
                                List<TicketScheme> schemes = new ArrayList<>();
                                if (!subtotalStrings.isEmpty() || !changeStrings.isEmpty()) { // may miss one of the two, accept anyway
                                    schemes.add(new TicketSchemeIT_PSCC(new Pair<>(restoredAmountT.first, restoredAmount),
                                            AmountComparator.getAboveTotalPrices(restoredAmountT.first, mainImage), AmountComparator.getBelowTotalPrices(restoredAmountT.first, mainImage)));
                                    OcrUtils.log(2, "MAN.AmountComparator", "Adding scheme: PSCC");
                                }
                                if (!subtotalStrings.isEmpty()) {
                                    schemes.add(new TicketSchemeIT_PSC(new Pair<>(restoredAmountT.first, restoredAmount),
                                            AmountComparator.getAboveTotalPrices(restoredAmountT.first, mainImage), AmountComparator.getBelowTotalPrices(restoredAmountT.first, mainImage)));
                                    OcrUtils.log(2, "MAN.AmountComparator", "Adding scheme: PSC");
                                }
                                if (!changeStrings.isEmpty()) {
                                    schemes.add(new TicketSchemeIT_PCC(new Pair<>(restoredAmountT.first, restoredAmount),
                                            AmountComparator.getAboveTotalPrices(restoredAmountT.first, mainImage), AmountComparator.getBelowTotalPrices(restoredAmountT.first, mainImage)));
                                    OcrUtils.log(2, "MAN.AmountComparator", "Adding scheme: PCC");
                                }
                                if (!schemes.isEmpty()) {
                                    OcrUtils.log(2, "", "Using custom restored amount comparator");
                                    amountComparator = new AmountComparator(schemes);
                                } else {
                                    OcrUtils.log(2, "", "Using default restored amount comparator");
                                    amountComparator = new AmountComparator(restoredAmount, restoredAmountT.first, mainImage); //9
                                }
                                bestRestoredTicketScheme = amountComparator.getBestAmount(false); //editTotal true => can correct the result
                                if (bestRestoredTicketScheme != null) {
                                    OcrUtils.log(2, "MANAGER.Comparator", "Best restored amount is: " + bestRestoredTicketScheme.obj().getBestAmount().second.setScale(2, RoundingMode.HALF_UP) +
                                            "\nwith scheme: " + bestRestoredTicketScheme.obj().toString() + "\nand score: " + bestRestoredTicketScheme.getScore());
                                }
                            }
                        }
                        if (newTicket.total != null)
                            break;
                        ++i;
                    }
                } else if (options.priceEditing == PriceEditing.ALLOW_VOID) {
                    //No matching word for total string. Try to find a price
                    List<Scored<OcrText>> possiblePrices = Stream.of(mainImage.getPricesTexts())
                            .map(price -> new Scored<>(ScoreFunc.getAmountScore(new Scored<>(0, price), mainImage), price))
                            .toList();
                    OcrUtils.log(2, "", "NO STRING FOR TOTAL FOUND\nPASSING TO VOID SEARCH");
                    Pair<OcrText, BigDecimal> amountT = DataAnalyzer.getMatchingAmount(possiblePrices, false);
                    if (amountT != null && newTicket.total == null) {
                        AmountComparator amountComparator = new AmountComparator(amountT.second, amountT.first, mainImage); //9
                        Scored<TicketScheme> tempTicketScheme = amountComparator.getBestAmount(false);
                        if (tempTicketScheme != null) {
                            OcrUtils.log(2, "MANAGER.Comparator", "Best amount is: " + tempTicketScheme.obj().getBestAmount().second.setScale(2, RoundingMode.HALF_UP) +
                                    "\nwith scheme: " + tempTicketScheme.obj().toString() + "\nand score: " + tempTicketScheme.getScore());
                            if (tempTicketScheme.getScore() > 1) { //This '1' will be changed later
                                bestTicketScheme = tempTicketScheme; //At least one hit must be found
                                newTicket.errors.add(OcrError.UNCERTAIN_TOTAL);
                            }
                        }
                    }
                }
                if (editTotal) {
                    if (bestRestoredTicketScheme != null) {
                        //if (bestRestoredTicketScheme.getScore() > 1 && (bestTicketScheme == null || bestRestoredTicketScheme.getScore() > bestTicketScheme.getScore())) { //This '1' will be changed later. Accept restored only if there is at least 1 hit
                        if (bestTicketScheme == null || bestRestoredTicketScheme.getScore() > bestTicketScheme.getScore()) {
                            bestTicketScheme = bestRestoredTicketScheme; //choose scheme with highest score.
                        }
                    }
                    if (bestTicketScheme != null) {
                        newTicket.total = bestTicketScheme.obj().getBestAmount().second; //10
                        amountPriceText = bestTicketScheme.obj().getBestAmount().first; //10
                        newTicket.errors.add(OcrError.TOTAL_EDITED);
                    }
                }

                ticket.containsCover = coverStrings.size() > 0;
                if (amountPriceText != null) {
                    ticket.totalRect = ImageProcessor.normalizeCoordinates(amountPriceText.box(), size(frame));
                }
            }
        }

        if (options.productsSearch != ProductsSearch.SKIP) {
            List<Pair<String, BigDecimal>> newProducts = null;
            if (bestTicketScheme != null) {
                List<Pair<OcrText, BigDecimal>> prices = bestTicketScheme.obj().getPricesList();
                if (prices != null) {
                    newProducts = Stream.of(prices)
                            .map(price -> new Pair<>(Stream.of(analyzer.getTextsOnleft(price.first)) //get texts for product name and concatenate these texts
                                    .reduce("", (string, text) -> string + text.text() + " "), price.second))
                            .toList();
                }
                if (IS_DEBUG_ENABLED && newProducts != null) {
                    OcrUtils.log(3, "LIST OF PRODUCTS:", "________________________________");
                    for (Pair<String, BigDecimal> product : newProducts) {
                        OcrUtils.log(3, "PRODUCT" , product.first + " with price: " + product.second.setScale(2, RoundingMode.HALF_UP));
                    }
                }
            }
            newTicket.products = newProducts;
        }

        ticket.total = newTicket.total;
        ticket.products = newTicket.products;
        ticket.errors.addAll(newTicket.errors);
        ticket.language = language;

        if (options.dateSearch != DateSearch.SKIP) {
            Pair<OcrText, Date> pair = findDate(texts, language != null
                    ? LANGUAGE_TO_COUNTRY.get(language) : null, country);
            if (pair != null)
                ticket.date = pair.second;
        }

        //NB: this is a fallback, a language could be associated to multiple countries, only one is chosen.
        if (country == null && languageScore > MIN_LANGUAGE_SCORE) {
            country = LANGUAGE_TO_COUNTRY.get(language);
            //update ticket currency
            ticket.currency = Currency.getInstance(country);
        }

        if (IS_DEBUG_ENABLED) {
            endTime = System.nanoTime();
            double duration = ((double) (endTime - startTime)) / 10e9;
            OcrUtils.log(1, "EXECUTION TIME: ", "Core: " + duration + " seconds");
        }
    }

    /**
     * Get a Ticket from an ImageProcessor. Some fields of the new ticket can be null.
     * <p> All possible errors inside {@link OcrTicket#errors} are listed in {@link OcrError}. </p>
     * @param imgProc {@link ImageProcessor} which has been set an image. Not modified by this method. Not null.
     * @param options define which operations to execute and detection accuracy. Not Null
     * @return {@link OcrTicket} Never null.
     *
     * @author Riccardo Zaglia
     */
    public synchronized OcrTicket getTicket(@NonNull ImageProcessor imgProc, OcrOptions options) {
        ImageProcessor procCopy = new ImageProcessor(imgProc);

        OcrTicket ticket = new OcrTicket();
        ticket.errors = new HashSet<>();
        if (!operative) {
            ticket.errors.add(OcrError.UNINITIALIZED);
            return ticket;
        }

        if (options.orientation != Orientation.FORCE_UPSIDE_DOWN)
            extractTicket(ticket, procCopy, options);

        boolean upsideDownAnlaysis = false;
        //if should find total and total is null, or should not find total but should find date and date is null
        boolean retryNeeded = (options.totalSearch != TotalSearch.SKIP && ticket.total == null)
                || (options.totalSearch == TotalSearch.SKIP
                && options.dateSearch != DateSearch.SKIP && ticket.date != null);
        if (options.orientation != Orientation.NORMAL && retryNeeded) {
            procCopy.rotate(2);
            extractTicket(ticket, procCopy, options);
            upsideDownAnlaysis = true;
        }

        if (options.totalSearch != TotalSearch.SKIP && ticket.total == null) {
            ticket.errors.add(OcrError.TOTAL_NOT_FOUND);
        }
        if (options.dateSearch != DateSearch.SKIP && ticket.date == null) {
            ticket.errors.add(OcrError.DATE_NOT_FOUND);
        }
        if (upsideDownAnlaysis && !ticket.errors.contains(OcrError.TOTAL_NOT_FOUND)
                && !ticket.errors.contains(OcrError.DATE_NOT_FOUND)) {
            ticket.errors.add(OcrError.UPSIDE_DOWN);
        }
        return ticket;
    }

    /**
     * Asynchronous version of {@link #getTicket(ImageProcessor, OcrOptions)}.
     * The {@link OcrTicket} is passed by the callback parameter.
     * @param imgProc {{@link ImageProcessor} which has been set an image. Not modified by this method. Not null.
     * @param options define which operations to execute and detection accuracy. Not Null
     * @param ticketCb callback to get the ticket. Not null.
     *
     * @author Riccardo Zaglia
     */
    public void getTicket(@NonNull ImageProcessor imgProc, OcrOptions options, @NonNull Consumer<OcrTicket> ticketCb) {
        new Thread(() -> ticketCb.accept(getTicket(imgProc, options))).start();
    }
}