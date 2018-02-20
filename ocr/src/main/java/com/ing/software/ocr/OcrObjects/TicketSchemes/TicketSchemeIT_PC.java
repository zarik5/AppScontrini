package com.ing.software.ocr.OcrObjects.TicketSchemes;

import android.support.annotation.NonNull;
import android.util.Pair;

import com.ing.software.common.Scored;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import com.annimon.stream.Stream;
import com.ing.software.ocr.OcrObjects.OcrText;
import com.ing.software.ocr.OcrUtils;

/**
 * @author Michelon
 * Scheme of a ticket with list of products and cash
 */

public class TicketSchemeIT_PC implements TicketScheme{

    private String tag = "IT_PC";
    private List<Pair<OcrText, BigDecimal>> products = new ArrayList<>();
    private BigDecimal total;
    private OcrText totalText;
    private BigDecimal cash;
    private Pair<OcrText, BigDecimal> bestAmount;
    private boolean acceptedList = false;
    private int NO_MATCH = 1; //MIN SCORE
    private final int THREE_VALUES = 80; //MAX SCORE

    /**
     * Constructor
     * @param total pair containing total and its text. Element of the pair may be null, not the pair itself.
     * @param aboveTotal texts above price. Not null. Ordered from top to bottom.
     * @param belowTotal list of prices below total. Not null. Ordered from top to bottom.
     */
    public TicketSchemeIT_PC(@NonNull Pair<OcrText, BigDecimal> total, @NonNull List<Pair<OcrText, BigDecimal>> aboveTotal, @NonNull List<BigDecimal> belowTotal) {
        bestAmount = total;
        this.total = total.second;
        this.totalText = total.first;
        this.products = new ArrayList<>(aboveTotal);
        if (aboveTotal.isEmpty())
            products = null;
        if (!belowTotal.isEmpty()) {
            cash = belowTotal.get(0);
        }
    }

    @Override
    public Pair<OcrText, BigDecimal> getBestAmount() {
        return bestAmount;
    }

    @Override
    public double getAmountScore(boolean strict) {
        Scored<Pair<OcrText, BigDecimal>> tempAmount = strict ? strictBestAmount() : looseBestAmount();
        if (tempAmount != null) {
            bestAmount = tempAmount.obj();
            return tempAmount.getScore();
        } else
            return -1;
    }

    @Override
    public String toString() {
        return tag;
    }

    @Override
    public List<Pair<OcrText, BigDecimal>> getPricesList() {
        return acceptedList ? products : null;
    }

    /**
     * This method checks if the ticket follows this scheme. It does not modify the amount.
     * @return max scored total if it follows this ticket scheme, min scored total otherwise
     */
    private Scored<Pair<OcrText, BigDecimal>> strictBestAmount() {
        if (products != null && total != null && cash != null) {
            BigDecimal productsSum = Stream.of(products)
                    .map(product -> product.second)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            OcrUtils.log(3, "TicketScheme_" + tag, "productsSum is: " + productsSum);
            OcrUtils.log(3, "TicketScheme_" + tag, "total is: " + total);
            OcrUtils.log(3, "TicketScheme_" + tag, "cash is: " + cash);
            if (productsSum.compareTo(total) == 0 && cash.compareTo(total) == 0) {
                acceptedList = true;
                return new Scored<>(THREE_VALUES, new Pair<>(totalText, total));
            } else {
                acceptedList = false;
            }
        }
        return new Scored<>(NO_MATCH, new Pair<>(totalText, total));
    }

    /**
     * This method tries to find the best amount considering all combinations of matches.
     * @return best amount according to arbitrary decisions
     */
    private Scored<Pair<OcrText, BigDecimal>> looseBestAmount() {
        int TWO_VALUES_AMOUNT = 50;
        int TWO_VALUES = 30;
        if (total != null) {
            if (products != null && cash != null) {
                BigDecimal productsSum = Stream.of(products)
                        .map(product -> product.second)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                OcrUtils.log(3, "TicketScheme_" + tag, "productsSum is: " + productsSum);
                OcrUtils.log(3, "TicketScheme_" + tag, "total is: " + total);
                OcrUtils.log(3, "TicketScheme_" + tag, "cash is: " + cash);
                boolean cashPrices = cash.compareTo(productsSum) == 0;
                boolean cashAmount = cash.compareTo(total) == 0;
                boolean pricesAmount = productsSum.compareTo(total) == 0;
                if (cashPrices || pricesAmount) {
                    acceptedList = true;
                    if (cashAmount) {
                        return new Scored<>(THREE_VALUES, new Pair<>(totalText, total));
                    } else if (pricesAmount) {
                        return new Scored<>(TWO_VALUES_AMOUNT, new Pair<>(totalText, total));
                    } else {
                        return new Scored<>(TWO_VALUES, new Pair<>(null, cash));
                    }
                } else {
                    return new Scored<>(NO_MATCH, new Pair<>(totalText, total));
                }
            } else if (products != null) {
                BigDecimal productsSum = Stream.of(products)
                        .map(product -> product.second)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                OcrUtils.log(3, "TicketScheme_" + tag, "productsSum is: " + productsSum);
                OcrUtils.log(3, "TicketScheme_" + tag, "total is: " + total);
                if (productsSum.compareTo(total) == 0) {
                    acceptedList = true;
                    return new Scored<>(TWO_VALUES_AMOUNT, new Pair<>(totalText, total));
                } else {
                    return new Scored<>(NO_MATCH, new Pair<>(totalText, total));
                }
            } else if (cash != null) {
                OcrUtils.log(3, "TicketScheme_" + tag, "total is: " + total);
                OcrUtils.log(3, "TicketScheme_" + tag, "cash is: " + cash);
                if (cash.compareTo(total) == 0) {
                    return new Scored<>(TWO_VALUES_AMOUNT, new Pair<>(totalText, total));
                } else {
                    return new Scored<>(NO_MATCH, new Pair<>(totalText, total));
                }
            } else {
                return new Scored<>(NO_MATCH, new Pair<>(totalText, total));
            }
        } else {
            //no total
            if (products != null && cash != null) {
                BigDecimal productsSum = Stream.of(products)
                        .map(product -> product.second)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                OcrUtils.log(3, "TicketScheme_" + tag, "productsSum is: " + productsSum);
                OcrUtils.log(3, "TicketScheme_" + tag, "total is: null");
                OcrUtils.log(3, "TicketScheme_" + tag, "cash is: " + cash);
                if (cash.compareTo(productsSum) == 0) {
                    acceptedList = true;
                    return new Scored<>(TWO_VALUES, new Pair<>(null, cash));
                }
            }
            return null;
        }
    }
}
