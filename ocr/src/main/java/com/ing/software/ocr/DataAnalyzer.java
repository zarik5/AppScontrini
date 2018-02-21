package com.ing.software.ocr;


import android.support.annotation.NonNull;
import android.support.annotation.Size;
import android.util.Pair;
import android.util.Range;
import android.util.SparseIntArray;

import com.ing.software.common.Scored;
import com.ing.software.common.Triple;
import com.ing.software.ocr.OcrObjects.OcrText;
import com.ing.software.ocr.OperativeObjects.ListAmountOrganizer;
import com.ing.software.ocr.OperativeObjects.RawImage;
import com.ing.software.ocr.OperativeObjects.ScoreFunc;
import com.ing.software.ocr.OperativeObjects.WordMatcher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.annimon.stream.Stream;

import static com.ing.software.ocr.OperativeObjects.ScoreFunc.NUMBER_MIN_VALUE;
import static java.util.Collections.*;
import static java.util.Arrays.*;
import static java.util.regex.Pattern.compile;

/**
 * Class used to extract information from raw data
 */
public class DataAnalyzer {

    /*
        @author Zaglia (date, price, word matchers)
        CONSTANTS:
     */


    private enum DateType {
        DMY,
        MDY;

        private static Map<Locale, DateType> DATE_TYPES =  new HashMap<>();
        static {
            DATE_TYPES.put(Locale.ITALY, DateType.DMY);
            DATE_TYPES.put(Locale.UK, DateType.DMY);
            DATE_TYPES.put(Locale.US, DateType.MDY);
        }

        static DateType fromCountry(Locale locale) { return DATE_TYPES.get(locale); }

        static List<DateType> all() { return asList(DMY, MDY); }
    }

    // go to https://regex101.com/ to check the behaviour of these regular expressions.
    //back reference/forward reference not supported in lookbehind but is supported in lookahead
//    static final Pattern DATE_DMY = compile(
//            "(?<!\\d)(0?[1-9]|[12]\\d|3[01])([-\\/.])(0?[1-9]|1[012])\\2((?:19)?[6-9]\\d|(?:20)?[0-5]\\d)(?!\\2|\\d)");

    private static final Pattern DATE = compile(
            "(?<!\\d)(\\d{1,4})([-\\/.])(\\d{1,2})\\2(\\d{1,4})(?!\\2|\\d)");
    // group 0 is the whole match, 2 is the delimiter
    private static final List<Integer> DATE_GROUPS = asList(1, 3, 4);
    private static final Range<Integer> YEAR_RANGE = new Range<>(1900, 2099);
    private static final int YEAR_CUT = 60; // YY < 60 -> 20YY;  YY >= 60 -> 19YY

    //In principle, multiple words should be matched with a space between them,
    //but since sometimes some words are split into multiple words, I remove all spaces all together
    //and match the words without spaces, even if there were in origin effectively distinct words.
    //The accepted errors (ex: O -> U,D) are based on common errors of the ocr scanning the dataset.
    //todo: consider importing this data from an external file
    private static final List<Pair<Locale, List<WordMatcher>>> TOTAL_MATCHERS = asList(
            new Pair<>(Locale.ITALIAN, asList(
                    new WordMatcher("T[OUD]TALE", 1),
                    new WordMatcher("T[OUD]TALEE[UI]R[OD]", 3),
                    new WordMatcher("IMP[OU]RT[OD]", 1),
                    new WordMatcher("TOT", 0),
                    new WordMatcher("TOTE[UI]R[OD]", 1),
                    new WordMatcher("IMP[OU]RT[OD]E[UI]R[OD]", 3)
            )),
            new Pair<>(Locale.ENGLISH, asList(
                    new WordMatcher("T[OUD]TAL", 1),
                    new WordMatcher("AMOUNT", 1),
                    new WordMatcher("GRANDTOTAL", 2)
            ))
    );

    private static final  List<Pair<Locale, List<WordMatcher>>> SUBTOTAL_MATCHERS = asList(
            new Pair<>(Locale.ITALIAN, asList(
                    new WordMatcher("SUBT[OD]TALE", 1)
            )),
            new Pair<>(Locale.ENGLISH, asList(
                    new WordMatcher("SUBTOTAL", 1)
            ))
    );

    private static final  List<Pair<Locale, List<WordMatcher>>> CASH_MATCHERS = asList(
            new Pair<>(Locale.ITALIAN, asList(
                    new WordMatcher("CONTANT[EI]", 1),
                    new WordMatcher("CARTADICREDITO", 3),
                    new WordMatcher("PAGAMENTOCONTANTE", 4),
                    new WordMatcher("CCRED", 0),
                    new WordMatcher("ASSEGNI", 1)
            )),
            new Pair<>(Locale.ENGLISH, asList(
                    new WordMatcher("CASH", 0)
            ))
    );

    private static final  List<Pair<Locale, List<WordMatcher>>> CHANGE_MATCHERS = asList(
            new Pair<>(Locale.ITALIAN, asList(
                    new WordMatcher("RESTO", 1)
            )),
            new Pair<>(Locale.ENGLISH, asList(
                    new WordMatcher("CHANGE", 1)
            ))
    );


    private static final  List<Pair<Locale, List<WordMatcher>>> COVER_MATCHERS = asList(
            new Pair<>(Locale.ITALIAN, asList(
                    new WordMatcher("COPERT[OI]", 1),
                    new WordMatcher("TAVOL[OI]", 1)
            )),
            new Pair<>(Locale.ENGLISH, asList(
                    new WordMatcher("COVER", 0),
                    new WordMatcher("TABLE", 0)
            ))
    );

    //searching tax with ITALIAN locale will return 0 matches
    private static final  List<Pair<Locale, List<WordMatcher>>> TAX_MATCHERS = asList(
            new Pair<>(Locale.ENGLISH, asList(
                    new WordMatcher("TAX", 0),
                    new WordMatcher("SALESTAX", 1)
            ))
    );

    //NB: these matchers use country locale instead of language locale
    private static final  List<Pair<Locale, List<WordMatcher>>> CURRENCY_MATCHERS = asList(
            new Pair<>(Locale.ITALY, asList(
                    new WordMatcher("EUR[OD]", 0),
                    new WordMatcher("EUR", 0)
            )),
            new Pair<>(Locale.UK, asList(
                    new WordMatcher("GBP", 0)
            )),
            new Pair<>(Locale.US, asList(
                    new WordMatcher("USD", 0)
            ))
    );

    //match a number between 2 and 4 digits,
    // or match any with 0 to 6 digits before dot and 1 to 2 digits after,
    // or match any with 1 to 6 digits before dot and 0 to 2 digits after,
    // optional minus in front, optional character before end of string (could be another digit).
    static final Pattern POTENTIAL_PRICE = compile(
            "(?<![\\d.,-])-?(?:\\d{2,4}|\\d{0,6}[.,]\\d{1,2}|\\d{1,6}[.,]\\d{0,2}) ?[^.,]?$");
    //match any number with a symbol for two decimals (a dot/comma or a space or a dot/comma + space),
    // optional thousands symbols, optional minus in front, optional character before end of string
    static final Pattern PRICE_WITH_SPACES = compile(
            "(?<![\\d.,'-])-?(?:0|[1-9]\\d{0,3}|[1-9]\\d{0,2}(?:(?:[.,'] |[.,' ])\\d{3})*)(?:[.,] |[., ])\\d{2}(?= ?[^\\d.,]?$)");
    static final Pattern PRICE_STRICT = compile(
            "(?<![\\d.,'-])-?(?:0|[1-9]\\d{0,3}|[1-9]\\d{0,2}(?:[.,']\\d{3})*)[.,]\\d{2}(?= ?[^\\d.,]?$)");
    //match any number with no points, optional minus in front, optional character before end of string
    static final Pattern PRICE_NO_DECIMALS = compile(
            "(?<![\\d.-])-?(?:0|[1-9]\\d*)(?= ?[^\\d.,]?$)");
    //match upside down prices. it's designed to reject corrupted upside down prices to avoid false positives.
    static final Pattern PRICE_UPSIDEDOWN = compile(
            "^[0OD1Il2ZEh5S9L8B6]{2} ?'[0OD1Il2ZEh5S9L8B6]+[^'.,]?$");
    //java does not support regex subroutines: I have to duplicate the character matching part

    //Used to sanitize price matched with PRICE_WITH_SPACES before cast to BigDecimal
    //the lookahead with anchor makes sure to match only (or exclude) last occurrence
    static final String DECIMAL_SEPARATOR = "(?:[.,] |[., ])(?=\\d{2}$)";
    static final String THOUSAND_SEPARATOR = "(?:[.,'] |[.,' ])(?!\\d{2}$)";



    /**
     * Get a list of texts where amount string is present
     * @param texts list of texts to analyze. Not null.
     * @return list of texts containing amount string with its score and language. Can be empty.
     */
    static List<Scored<Pair<OcrText, Locale>>> findAmountStringTexts(List<OcrText> texts) {
        return findAllMatchesWithLanguage(texts, TOTAL_MATCHERS);
    }

    /**
     * Get a list of texts where subtotal string is present
     * @param texts list of texts to analyze. Not null.
     * @return list of texts containing subtotal string with its score and language. Can be empty.
     */
    static List<Scored<Pair<OcrText, Locale>>> findSubtotalStringTexts(List<OcrText> texts) {
        return findAllMatchesWithLanguage(texts, SUBTOTAL_MATCHERS);
    }

    /**
     * Get a list of texts where cash string is present
     * @param texts list of texts to analyze. Not null.
     * @return list of texts containing cash string with its score and language. Can be empty.
     */
    static List<Scored<Pair<OcrText, Locale>>> findCashStringTexts(List<OcrText> texts) {
        return findAllMatchesWithLanguage(texts, CASH_MATCHERS);
    }

    /**
     * Get a list of texts where change string is present
     * @param texts list of texts to analyze. Not null.
     * @return list of texts containing change string with its score and language. Can be empty.
     */
    static List<Scored<Pair<OcrText, Locale>>> findChangeStringTexts(List<OcrText> texts) {
        return findAllMatchesWithLanguage(texts, CHANGE_MATCHERS);
    }

    /**
     * Get a list of texts where cover string is present
     * @param texts list of texts to analyze. Not null.
     * @return list of texts containing cover string with its score and language. Can be empty.
     */
    static List<Scored<Pair<OcrText, Locale>>> findCoverStringTexts(List<OcrText> texts) {
        return findAllMatchesWithLanguage(texts, COVER_MATCHERS);
    }

    /**
     * Get a list of texts where tax string is present
     * @param texts list of texts to analyze. Not null.
     * @return list of texts containing tax string with its score and language. Can be empty.
     */
    static List<Scored<Pair<OcrText, Locale>>> findTaxStringTexts(List<OcrText> texts) {
        return findAllMatchesWithLanguage(texts, TAX_MATCHERS);
    }

    /**
     * @author Zaglia
     * Find the most probable ticket language from al list of matches with associated language
     * @param matches list of matches with associated language. Can be emty. Not null.
     * @return best language locale. Can be empty if matches is empty.
     */
    // not using varargs because of possible heap pollution??
    static Scored<Locale> getBestLanguage(List<List<Scored<Pair<OcrText, Locale>>>> matches) {
        Locale bestLanguage = null;
        double bestScore = 0;
        Map<Locale, Double> accumulator = new HashMap<>();
        for (int i = 0; i < matches.size(); i++) {
            for (int j = 0; j < matches.get(i).size(); j++) {
                Locale matchLang = matches.get(i).get(j).obj().second;
                double matchScore = matches.get(i).get(j).getScore();

                Double maybeScore = accumulator.get(matchLang);
                double newScore = maybeScore != null ? maybeScore + matchScore : matchScore;
                accumulator.put(matchLang, newScore);
                if (newScore > bestScore) {
                    bestScore = newScore;
                    bestLanguage = matchLang;
                }
            }
        }
        return new Scored<>(bestScore, bestLanguage);
    }

    /**
     * @author Zaglia
     * Remove all matches in a list of matches (with associated language locale) that
     * do not belong to specified language
     * @param matches list of matches with associated language locale. Not null.
     * @param language language of matches to keep. Non null.
     * @return filtered matches. Can be empty if no match remains.
     */
    static List<Scored<OcrText>> filterForLanguage(List<Scored<Pair<OcrText, Locale>>> matches, Locale language) {
        return Stream.of(matches)
                .filter(match -> match.obj().second == language)
                .map(scored -> new Scored<>(scored.getScore(), scored.obj().first))
                .toList();
    }

    /**
     * @author Michelon
     * Insert detected amount texts in a listAmountOrganizer
     * @param texts list of scored source texts
     * @param mainImage source image
     * @return list of listAmountOrganizer containing source texts
     */
    static List<ListAmountOrganizer> organizeAmountList(@NonNull List<Scored<OcrText>> texts, RawImage mainImage) {
        return Stream.of(texts)
                    .map(source -> new ListAmountOrganizer(source, mainImage))
                    .toList();
    }

    /**
     * @author Riccardo Zaglia
     * Find all OcrText which text is matched by any of the list of matchers.
     * @param lines list of OcrTexts. Can be empty. Not null.
     * @return OcrTexts matched. Can be empty if no match is found.
     */
    @Deprecated
    static List<Scored<OcrText>> findAllMatchingTexts(List<OcrText> lines, List<WordMatcher> matchers) {
        return Stream.of(lines)
                .map(line -> new Scored<>(max(Stream.of(matchers).map(m -> m.match(line)).toList()), line))
                .filter(s -> s.getScore() > 0)
                .toList();
    }

    /**
     * @author Zaglia
     * Find all matches in a list of texts and assign a language and a fit score to each one.
     * The same text can appear in multiple matches if it can be interpreted in multiple languages
     * @param texts list of OcrTexts. Not modified. Can be empty. Not null.
     * @param languageMatchers list ontaining lists of WordMatchers categorized by a Locale (language).
     *                         Can be empty. Not null.
     * @return list of scored matches with associated language. Can be empty.
     */
    private static List<Scored<Pair<OcrText, Locale>>> findAllMatchesWithLanguage(
            List<OcrText> texts, List<Pair<Locale, List<WordMatcher>>> languageMatchers) {
        List<Scored<Pair<OcrText, Locale>>> matches = new ArrayList<>();
        for (OcrText text : texts) {
            List<Scored<Locale>> scoredLocales = Stream.of(languageMatchers)
                    .map(pair -> new Scored<>(
                            max(Stream.of(pair.second).map(m -> m.match(text)).toList()),
                            pair.first))
                    .filter(s -> s.getScore() > 0)
                    .toList();
            if (scoredLocales.size() > 0) {
                matches.add(new Scored<>(scoredLocales.get(0).getScore(),
                        new Pair<>(text, scoredLocales.get(0).obj())));
            }
        }
        return matches;
    }

    /**
     * @author Zaglia
     * Choose a currency country based on the number of matches of the currency abbreviations.
     * @param texts list of OcrTexts. Not modified. Can be empty. Not null.
     * @return chosen country. Can be null if no match is found.
     */
    static Locale getCurrencyCountry(List<OcrText> texts) {
        List<Scored<Pair<OcrText, Locale>>> matches = findAllMatchesWithLanguage(texts, CURRENCY_MATCHERS);
        Locale bestCountry = null;
        int bestScore = 0;
        Map<Locale, Integer> accumulator = new HashMap<>();
        for (int i = 0; i < matches.size(); i++) {
            Locale match = matches.get(i).obj().second;
            Integer counter = accumulator.get(match);
            int score = counter != null ? counter + 1 : 1;
            accumulator.put(match, score);
            if (score > bestScore) {
                bestScore = score;
                bestCountry = match;
            }
        }
        return bestCountry;
    }

    /**
     * Get possible amount from word matcher and regex
     * @param texts list of scored target texts (prices). Not null.
     * @return text containing amount price and its decoded value
     */
    static Pair<OcrText, BigDecimal> getMatchingAmount(@NonNull List<Scored<OcrText>> texts, boolean advanced) {
        List<Pair<OcrText, BigDecimal>> prices = findAllPricesRegex(Stream.of(texts).map(Scored::obj).toList(), advanced);
        if (prices.size() > 0)
            return prices.get(0);
        return null;
    }

    /**
     * @author Zaglia
     * Convert a price regex match into a BigDecimal
     * @param match regex match
     * @return BigDecimal or null if error.
     */
    private static BigDecimal getRegexPriceValue(String match) {
        String sanitized = match.replaceAll(DECIMAL_SEPARATOR, ".");
        sanitized = sanitized.replaceAll(THOUSAND_SEPARATOR, "");
        //since price regex accept dots, commas and spaces for both decimal and thousands symbol,
        //the sanitized string could still be an invalid number.
        try {
            return new BigDecimal(sanitized);
        }
        catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Find all OcrTexts that matches a price with regex
     * @param lines List of OcrTexts
     * @return list of pairs of OcrText and associated price string
     *
     * @author Zaglia
     */
    static List<Pair<OcrText, BigDecimal>> findAllPricesRegex(List<OcrText> lines, boolean advanced) {
        List<Pair<OcrText, BigDecimal>> prices = new ArrayList<>();
        for (OcrText line : lines) {
            Matcher matcher = PRICE_WITH_SPACES.matcher(advanced ? line.sanitizedAdvancedNum()
                    : line.sanitizedNum());
            if (matcher.find()) {
                BigDecimal price = getRegexPriceValue(matcher.group());
                if (price != null) {
                    prices.add(new Pair<>(line, price));
                }
            }
        }
        return prices;
    }

    /**
     * @author Michelon
     * Get restored amount without regex
     * @param texts list of scored target texts (prices). Not null.
     * @return text containing amount price and it's decoded value
     */
    static Pair<OcrText, BigDecimal> getRestoredAmount(@NonNull List<Scored<OcrText>> texts) {
        for (Scored<OcrText> singleText : texts) {
            if (ScoreFunc.isPossiblePriceNumber(singleText.obj().textNoSpaces(), singleText.obj().sanitizedNum()) < NUMBER_MIN_VALUE) {
                BigDecimal amount = analyzeAmount(singleText.obj().sanitizedAdvancedNum());
                if (amount != null)
                    return new Pair<>(singleText.obj(), amount);
            }
        }
        return null;
    }

    /**
     * @author Zaglia
     * Find all dates, eventually restristed to a specific country date format.
     * @param texts list of OcrTexts
     * @param forcedCountry forced country locale. If null then all date formats are tried to match.
     * @return list of triple containing the date, country and OcrText. The same OcrText could be included
     * in multiple triples if the date match could be interpreted in multiple formats.
     */
    private static List<Triple<OcrText, Date, DateType>> findAllDatesRegex(List<OcrText> texts, Locale forcedCountry) {
        List<DateType> formats = forcedCountry != null
                ? singletonList(DateType.fromCountry(forcedCountry))
                : DateType.all();
        //using map makes sure that if the same date is repeated inside the ticket,
        // it will no be rejected due to multiple matches.
        Map<Date, Pair<OcrText, DateType>> dates = new HashMap<>();
        for (OcrText text : texts) {
            Matcher matcher = DATE.matcher(text.sanitizedNum());
            if (matcher.find()) {
                SparseIntArray groups = new SparseIntArray(3);
                for (Integer idx : DATE_GROUPS) {
                    groups.append(idx, Integer.valueOf(matcher.group(idx)));
                }
                for (DateType fmt : formats) {
                    int year = 0, month = 0, day = 0;
                    if (fmt == DateType.DMY) {
                        day = groups.get(DATE_GROUPS.get(0));
                        month = groups.get(DATE_GROUPS.get(1));
                        year = groups.get(DATE_GROUPS.get(2));
                    } else if (fmt == DateType.MDY) {
                        month = groups.get(DATE_GROUPS.get(0));
                        day = groups.get(DATE_GROUPS.get(1));
                        year = groups.get(DATE_GROUPS.get(2));
                    }

                    // here I define some constants inline because they are not meant to be changed ever.
                    //Eg: any 2 digit year is always < 100; the months are always 12, etc.
                    boolean validYear = year < 100 || YEAR_RANGE.contains(year);
                    boolean validMonth = month >= 1 && month <= 12;
                    boolean validDay = day >= 1 && (asList(4, 6, 9, 11).contains(month)
                            ? day < 30 : (month == 2 ? day < 29 : day < 31));
                    //for convenience I do not check for leap years and other date exceptions, in the rare
                    // occurrence of a matched date with a non leap year, february month and 29th day,
                    // the gregorian calendar overflows to march 1st without throwing exceptions.
                    if (validYear && validMonth && validDay) {
                        if (year < 100)
                            year += year > YEAR_CUT ? 1900 : 2000;
                        // correct for 0 based month
                        Date date = new GregorianCalendar(year, month - 1, day).getTime();
                        dates.put(date, new Pair<>(text, fmt));
                    }
                }
            }
            // It's better to avoid word concatenation because it could match a wrong date.
            // Ex: 1/1/20 14:30 -> 1/1/2014:30
        }
        return Stream.of(dates)
                .map(entry -> new Triple<>(entry.getValue().first, entry.getKey(), entry.getValue().second))
                .toList();
    }

    /**
     * Find date. Date is rejected if there are multiple and disambiguation has failed.
     * @param texts List of OcrTexts.
     * @param suggestedCountry country locale used for disambiguation. Can be null.
     * @param forcedCountry forced country locale. Can be null.
     * @return triple containing OcrText, date and locale. Can be null if no date found or multiple.
     */
    static Pair<OcrText, Date> findDate(
            List<OcrText> texts, Locale suggestedCountry, Locale forcedCountry) {
        List<Triple<OcrText, Date, DateType>> matches = findAllDatesRegex(texts, forcedCountry);
        if (matches.size() >= 1) {
            Triple<OcrText, Date, DateType> firstMatch = matches.get(0);
            if (forcedCountry != null || suggestedCountry == null) {
                return matches.size() == 1 ? new Pair<>(firstMatch.first, firstMatch.second) : null;
            }
            DateType suggestedType = DateType.fromCountry(suggestedCountry);
            //map containing pairs of (first: match counter, second: last match index).
            Map<DateType, Pair<Integer, Integer>> accumulator = new HashMap<>();
            for (int i = 0; i < matches.size(); i++) {
                DateType type = matches.get(i).third;
                Pair<Integer, Integer> pair = accumulator.get(type);
                accumulator.put(type, new Pair<>(pair.first != null ? pair.first + 1 : 1, i));
            }
            Pair<Integer, Integer> pair = accumulator.get(suggestedType);
            if (pair != null) {
                Triple<OcrText, Date, DateType> match = matches.get(pair.second);
                // discard match if counter is > 1 -> date is ambiguous
                return pair.first == 1 ? new Pair<>(match.first, match.second) : null;
            } else {
                return matches.size() == 1 ? new Pair<>(firstMatch.first, firstMatch.second) : null;
            }
        } else {
            return null;
        }
    }

    /*
    Old analysis. Used alongside regex.
     */

    /**
     * @author Michelon
     * Tries to find a BigDecimal from string
     * @param amountString string containing possible amount. Length > 0.
     * @return BigDecimal containing the amount, null if no number was found
     */
    public static BigDecimal analyzeAmount(@Size(min = 1) String amountString) {
        BigDecimal amount = null;
        try {
            String decoded = deepAnalyzeAmountChars(amountString);
            if (!decoded.equals(""))
                amount = new BigDecimal(decoded);
        } catch (Exception e1) {
            amount = null;
        }
        return amount;
    }

    /**
     * @author Michelon
     * Analyze a string (reversed by this method) looking for a number (with two decimals).
     * Uses arbitrary decisions.
     * @param targetAmount string containing possible amount. Length > 0.
     * @return string containing the amount, empty stringBuilder if nothing found
     */
    private static String deepAnalyzeAmountChars(@Size(min = 1) String targetAmount){
        StringBuilder manipulatedAmount;
        StringBuilder reversedAmount = new StringBuilder(targetAmount).reverse();
        OcrUtils.log(6,"deepAnalyzeAmount", "Deep amount analysis for: " + targetAmount);
        OcrUtils.log(7,"deepAnalyzeAmount", "Reversed amount is: " + reversedAmount.toString());
        manipulatedAmount = analyzeCharsLong(reversedAmount.toString());
        OcrUtils.log(6,"deepAnalyzeAmount", "Analyzed amount is: " + manipulatedAmount.toString());
        return manipulatedAmount.reverse().toString();
    }

    /**
     * @author Michelon
     * Analyze a string looking for a number (with two decimals)
     * @param source string containing possible amount. Length > 0.
     * @return stringBuilder containing the amount, empty stringBuilder if nothing found
     */
    private static StringBuilder analyzeCharsLong(@Size (min = 1) String source) {
        boolean isNegative = (source.charAt(source.length()-1) == '-');
        source = removeLetters(source);
        StringBuilder manipulatedAmount = new StringBuilder();
        //Check if there are at least 2 dec + '.' + 1 num = 4 chars
        if (source.length() >= 4) {
            char char0 = source.charAt(0);
            char char1 = source.charAt(1);
            char char2 = source.charAt(2);
            char char3 = source.charAt(3);
            if (char0 == '.' && char1 != '.' && char2 != '.' && char3 != '.')
                manipulatedAmount.append("00").append(char0).append(char1).append(removeRedundantPoints(source.substring(2)));
            else if (char0 == '.') //There is another '.' remove the first one
                return analyzeCharsLong(source.substring(1));
            else if (char1 == '.' && Character.isDigit(char2) && Character.isDigit(char3)) //now char0 must be digit
                manipulatedAmount.append("0").append(char0).append(char1).append(removeRedundantPoints(source.substring(2)));
            else if (char1 == '.') //char0 must be digit and char2 or char3 must be '.' = remove char1
                return analyzeCharsLong(String.valueOf(char0) + source.substring(2));
            else if (char2 == '.') //char0 and char1 must be digit = if char2 is '.' everything is ok
                manipulatedAmount.append(char0).append(char1).append(char2).append(removeRedundantPoints(source.substring(3)));
            else if (char3 == '.') //char0, 1, 2 must be digit = if char3 is '.' char0 was added by detector = remove it
                return analyzeCharsLong(source.substring(1));
            else //we have 4 digits, suppose we did not find the '.' = add it after char0 and char1
                manipulatedAmount.append(char0).append(char1).append('.').append(removeRedundantPoints(source.substring(2)));
            if (isNegative)
                manipulatedAmount.append("-");
            return manipulatedAmount;
        } else if (source.length() > 0 && isNegative)
            return analyzeChars(source).append("-");
        else if (source.length() > 0)
            return analyzeChars(source);
        else
            return manipulatedAmount;
    }

    /**
     * @author Michelon
     * Keep only digits and points in a string
     * @param string source string. Length > 0.
     * @return string with only digits and '.'
     */
    private static String removeLetters(@Size(min = 1) String string) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < string.length(); ++i)
            if (Character.isDigit(string.charAt(i)) || string.charAt(i) == '.')
                result.append(string.charAt(i));
        return result.toString();
    }

    /**
     * @author Michelon
     * Removes '.' from a string
     * @param string source string. Length > 0
     * @return string with no '.'
     */
    private static String removeRedundantPoints(@Size(min = 1) String string) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < string.length(); ++i)
            if (string.charAt(i) != '.')
                result.append(string.charAt(i));
        return result.toString();
    }

    /**
     * @author Michelon
     * Analyze a string looking for a number with two decimals
     * @param source source string @Size(min = 1, max = 3)
     * @return StringBuilder with decoded number (empty if nothing found)
     */
    private static StringBuilder analyzeChars(@Size(min = 1) String source) {
        if (source.length()==3)
            return analyzeChars(source.charAt(0), source.charAt(1), source.charAt(2));
        else if (source.length() == 2)
            return analyzeChars(source.charAt(0), source.charAt(1));
        else
            return analyzeChars(source.charAt(0));
    }

    /**
     * @author Michelon
     * Analyze three chars, uses arbitrary decisions to extract a number with two decimals.
     * @param char0 first char. Not null.
     * @param char1 second char. Not null.
     * @param char2 third char. Not null.
     * @return StringBuilder with decoded number (empty if nothing found)
     */
    private static StringBuilder analyzeChars(char char0, char char1, char char2) {
        StringBuilder result = new StringBuilder();
        if (Character.isDigit(char0) && Character.isDigit(char1) && Character.isDigit(char2)) {
            //point is missing = add it after char0 and char1
            return result.append(char0).append(char1).append(".").append(char2);
        } else if (Character.isDigit(char0) && char1 == '.' && Character.isDigit(char2)) {
            //one decimal is missing, suppose it's the last one
            return  result.append("0").append(char0).append(char1).append(char2);
        } else if (char0 == '.' && Character.isDigit(char1) && Character.isDigit(char2)) {
            //Missing both decimals (or maybe found a point that shouldn't be there)
            return result.append("00").append(char0).append(char1).append(char2);
        } else if (Character.isDigit(char0) && Character.isDigit(char1) && char2 == '.') {
            //Missing first integer, use 0
            return result.append(char0).append(char1).append(char2).append("0");
        } else if (Character.isDigit(char0)) //char1 and char2 must be points
            return analyzeChars(char0, char1);
        else //char0 is point, can be removed as char1 or char2 is also a point
            return analyzeChars(char1, char2);
    }

    /**
     * @author Michelon
     * Analyze two chars, uses arbitrary decisions to extract a number with two decimals.
     * @param char0 first char. Not null.
     * @param char1 second char. Not null.
     * @return StringBuilder with decoded number (empty if nothing found)
     */
    private static StringBuilder analyzeChars(char char0, char char1) {
        StringBuilder result = new StringBuilder();
        if (Character.isDigit(char0) && Character.isDigit(char1)) {
            //point is missing = add after char0 and char1 and add '0' (actually this may be anything)
            return result.append(char0).append(char1).append(".0");
        } else if (Character.isDigit(char0) && char1 == '.') {
            //one decimal is missing, suppose it's the last one
            return  result.append("0").append(char0).append(char1).append("0");
        } else if (char0 == '.' && Character.isDigit(char1)) {
            //Missing both decimals (or maybe found a point that shouldn't be there)
            return result.append("00").append(char0).append(char1);
        } else //Both are points
            return result;
    }

    /**
     * @author Michelon
     * Analyze single char, uses arbitrary decisions to extract a number with two decimals.
     * @param char0 first char. Not null.
     * @return StringBuilder with decoded number (empty if nothing found)
     */
    private static StringBuilder analyzeChars(char char0) {
        StringBuilder result = new StringBuilder();
        if (Character.isDigit(char0))
            return result.append("00.").append(char0);
        else
            return result;
    }
}
