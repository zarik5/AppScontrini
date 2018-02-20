package com.ing.software.ocr.OperativeObjects;

import android.util.Pair;

import com.ing.software.common.Scored;
import com.ing.software.ocr.DataAnalyzer;
import com.ing.software.ocr.OcrObjects.OcrText;
import com.ing.software.ocr.OcrObjects.TicketSchemes.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.annimon.stream.Stream;

import static com.ing.software.ocr.OperativeObjects.ScoreFunc.NUMBER_MIN_VALUE;

/**
 * Class to analyze amount and compare it with predefined tickets.
 */

public class AmountComparator {

    private List<TicketScheme> acceptedSchemes = new ArrayList<>();

    /**
     * @author Michelon
     * Constructor, check amount against a defined scheme
     * @param scheme scheme to use to check amount
     */
    public AmountComparator(TicketScheme scheme) {
        acceptedSchemes.add(scheme);
    }

    /**
     * @author Michelon
     * Constructor, check amount against a list of schemes
     * @param schemes list of schemes to use to check amount
     */
    public AmountComparator(List<TicketScheme> schemes) {
        acceptedSchemes.addAll(schemes);
    }

    /**
     * @author Michelon
     * Constructor, check amount against all schemes
     * @param amount BigDecimal containing decoded amount.
     * @param amountText text containing amount price
     * @param mainImage source rawImage
     */
    public AmountComparator(BigDecimal amount, OcrText amountText, RawImage mainImage) {
        acceptedSchemes = getAllSchemes(new Pair<>(amountText, amount), getAboveTotalPrices(amountText, mainImage), getBelowTotalPrices(amountText, mainImage));
    }

    /**
     * @author Michelon
     * Retrieves amount with highest score from ticket schemes
     * @param strict true if you want only a confirm of the amount, false if you want the price with highest score
     * @return ticket containing amount with highest score. Null if no valid amount was found.
     */
    public Scored<TicketScheme> getBestAmount(boolean strict) {
        List<Scored<TicketScheme>> bestAmounts = Stream.of(acceptedSchemes)
                                        .filter(scheme -> scheme.getAmountScore(strict) >= 0)
                                        .map(scheme -> new Scored<>(scheme.getAmountScore(strict), scheme))
                                        .sorted(Collections.reverseOrder())
                                        .toList();
        return bestAmounts.isEmpty()? null : bestAmounts.get(0);
    }

    /**
     * @author Michelon
     * Analyze the list of products from OcrSchemer and convert accepted values as bigdecimal
     * @param amountText text containing amount price. Not null.
     * @param mainImage source rawImage
     * @return list of bigDecimal and texts of numbers above total
     */
    public static List<Pair<OcrText, BigDecimal>> getAboveTotalPrices(OcrText amountText, RawImage mainImage) {
        Collections.sort(mainImage.getPricesTexts());
        return Stream.of(mainImage.getPricesTexts())
                .filter(price -> price.box().centerY() < amountText.box().centerY())
                .filter(price -> ScoreFunc.isPossiblePriceNumber(price.textNoSpaces(), price.sanitizedNum()) < NUMBER_MIN_VALUE)
                .map(price -> new Pair<>(price, DataAnalyzer.analyzeAmount(price.sanitizedAdvancedNum())))
                .filter(price -> price.second != null)
                .withoutNulls()
                .toList();
    }

    /**
     * @author Michelon
     * Analyze the list of products from OcrSchemer and convert accepted values as bigdecimal
     * @param amountText text containing amount price. Not null.
     * @param mainImage source rawImage
     * @return list of bigDecimal of numbers below total
     */
    public static List<BigDecimal> getBelowTotalPrices(OcrText amountText, RawImage mainImage) {
        Collections.sort(mainImage.getPricesTexts());
        return Stream.of(mainImage.getPricesTexts())
                .filter(price -> price.box().centerY() > amountText.box().centerY())
                .filter(price -> ScoreFunc.isPossiblePriceNumber(price.textNoSpaces(), price.sanitizedNum()) < NUMBER_MIN_VALUE)
                .map(price -> DataAnalyzer.analyzeAmount(price.sanitizedAdvancedNum()))
                .withoutNulls()
                .toList();
    }

    /**
     * @author Michelon
     * Initialize a list of all TicketSchemes
     * @param total text containing amount price (text + bigDecimal)
     * @param aboveTotal list of prices (with text) above total
     * @param belowTotal list of prices below total
     * @return list of all ticketSchemes
     */
    private static List<TicketScheme> getAllSchemes(Pair<OcrText, BigDecimal> total, List<Pair<OcrText, BigDecimal>> aboveTotal, List<BigDecimal> belowTotal) {
        TicketScheme[] schemes = {
                new TicketSchemeIT_PC(total, aboveTotal, belowTotal),
                new TicketSchemeIT_PCC(total, aboveTotal, belowTotal),
                new TicketSchemeIT_PSC(total, aboveTotal, belowTotal),
                new TicketSchemeIT_PSCC(total, aboveTotal, belowTotal)
        };
        return Arrays.asList(schemes);
    }
}
