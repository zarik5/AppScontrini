package com.ing.software.ocr;

import android.graphics.RectF;
import android.util.Pair;
import android.util.SizeF;

import com.annimon.stream.Stream;
import com.ing.software.common.CommonUtils;
import com.ing.software.common.Scored;
import com.ing.software.ocr.OcrObjects.OcrText;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import static com.ing.software.ocr.DataAnalyzer.POTENTIAL_PRICE;
import static com.ing.software.ocr.DataAnalyzer.PRICE_UPSIDEDOWN;
import static java.lang.Math.abs;

//todo use new functions instead
public class TestFunctions {

    // Extended rectangle vertical multiplier
    private static final float EXT_RECT_V_MUL = 3;

    /**
     * Choose the OcrText that most probably contains the amount string.
     * Criteria: AMOUNT_MATCHER score; character size; higher in the photo
     * @param lines list of OcrLines. Can be empty
     * @return OcrText with higher score. Can be null if no match is found.
     *
     * @author Riccardo Zaglia
     */
    // todo: find most meaningful way to combine score criteria.
    // Do not order. Use max() to get best scored Text, otherwise if you have to read all these texts anyway,
    // ordering it's useless.
    private static List<Scored<Pair<OcrText, Locale>>> findAllScoredAmountStrings(List<OcrText> lines, SizeF bmSize) {
        List<Scored<Pair<OcrText, Locale>>> matchedLines = DataAnalyzer.findAmountStringTexts(lines);
        // modify score for each matched line
        for (Scored<Pair<OcrText, Locale>> line : matchedLines) {
            double score = line.getScore();
            score *= line.obj().first.charWidth() + line.obj().first.charHeight(); // <- todo normalize with all texts average
            score *= 1. - line.obj().first.box().centerY() / bmSize.getHeight(); // <- todo find a better way
            // (relative position with cash and change)
            line.setScore(score);
        }
        return matchedLines;
    }

    //todo: find cash, change, subtotal, indoor.

    /**
     * Get a strip rectangle where the amount price should be found.
     * @param amountStr amount string line.
     * @param bmSize bitmap size.
     * @return RectF rectangle in bitmap space.
     *
     * @author Riccardo Zaglia
     */
    private static RectF getAmountStripRect(OcrText amountStr, SizeF bmSize) {
        //I use box height because if text is crooked, I search for the amount price in a larger area.
        // todo: take into account slope to get more accurate results
        float halfHeight = amountStr.box().height() * EXT_RECT_V_MUL / 2f;
        // here I account that the amount number could be in the same OcrText as the amount string.
        // I use the center of the OcrText as a left boundary.
        return new RectF(amountStr.box().centerX(), amountStr.box().centerY() - halfHeight,
                bmSize.getWidth(), amountStr.box().centerY() + halfHeight);
    }


    /**
     * Get all texts that are potentially prices, but could be corrupt
     * @param lines
     * @return
     */
    private static List<OcrText> findAllPotentialPrices(List<OcrText> lines) {
        //for each line, keep line if matches price for textNoSpaces.
        //I do not use sanitized strings because they easily matches at random.
        // So there could be some texts that are matched by certainPrices but not by this
        //todo: discuss decision

        return Stream.of(lines).filter(line -> POTENTIAL_PRICE.matcher(line.textNoSpaces()).find()).toList();
    }

    /**
     * Get all texts that matches an upside down price
     * @param lines
     * @return
     */
    private static List<OcrText> findAllUpsideDownPrices(List<OcrText> lines) {
        return Stream.of(lines).filter(line -> PRICE_UPSIDEDOWN.matcher(line.textNoSpaces()).find()).toList();
    }

    /**
     * Choose the OcrText that most probably contains the amount price and return it as a BigDecimal.
     * Criteria: lower distance from center of strip; least character size difference from amount string
     * @param lines all lines contained inside amount strip, converted to original bitmap space
     * @param amountStr amount string line.
     * @param stripRect amount strip rect in the original bitmap space.
     * @return BigDecimal containing price, or null if no price found.
     */
    // todo: find most meaningful way to combine score criteria.
    // todo: reject false positives adding a lower limit to the score > 0.
    private static BigDecimal findAmountPrice (List<OcrText> lines, OcrText amountStr, RectF stripRect) {
        List<Pair<OcrText, BigDecimal>> prices = DataAnalyzer.findAllPricesRegex(lines, true);
        BigDecimal price = null;
        double bestScore = 0;
        for (Pair<OcrText, BigDecimal> priceLine : prices) {
            OcrText line = priceLine.first;
            double score = 1. - abs(line.box().centerY() - stripRect.centerY()) / stripRect.height(); // y position diff
            score *= 1. - abs(line.charWidth() - amountStr.charWidth()) / amountStr.charWidth(); // char width diff
            score *= 1. - abs(line.charHeight() - amountStr.charHeight()) / amountStr.charHeight(); // char height diff
            if (score > bestScore && priceLine.second != null) {
                bestScore = score;
                price = priceLine.second;
            }
        }
        return price;
    }
}
