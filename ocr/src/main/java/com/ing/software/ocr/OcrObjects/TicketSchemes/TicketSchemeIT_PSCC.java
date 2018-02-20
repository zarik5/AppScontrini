package com.ing.software.ocr.OcrObjects.TicketSchemes;

import android.support.annotation.NonNull;
import android.util.Pair;

import com.annimon.stream.Stream;
import com.ing.software.common.Scored;
import com.ing.software.ocr.OcrObjects.OcrText;
import com.ing.software.ocr.OcrUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


/**
 *@author Michelon
 * Scheme of a ticket with list of products, subtotal, cash and change
 */

public class TicketSchemeIT_PSCC implements TicketScheme{

    private String tag = "IT_PSCC";
    private List<Pair<OcrText, BigDecimal>> products = new ArrayList<>();
    private BigDecimal subtotal;
    private BigDecimal total;
    private OcrText totalText;
    private BigDecimal cash;
    private BigDecimal change;
    private Pair<OcrText, BigDecimal> bestAmount;
    private boolean acceptedList = false;
    private int NO_MATCH = 1;
    private final int FOUR_VALUES = 100;

    /**
     * Constructor
     * @param total pair containing total and its text. Element of the pair may be null, not the pair itself.
     * @param aboveTotal texts above price. Not null. Ordered from top to bottom.
     * @param belowTotal list of prices below total. Not null. Ordered from top to bottom.
     */
    public TicketSchemeIT_PSCC(Pair<OcrText, BigDecimal> total, @NonNull List<Pair<OcrText, BigDecimal>> aboveTotal, @NonNull List<BigDecimal> belowTotal) {
        bestAmount = total;
        this.total = total.second;
        this.totalText = total.first;
        if (!aboveTotal.isEmpty()) {
            this.subtotal = aboveTotal.get(aboveTotal.size() - 1).second;
        }
        if (aboveTotal.size() > 1) {
            products = new ArrayList<>(aboveTotal);
            products.remove(aboveTotal.size() - 1); //remove subtotal
        }
        if (!belowTotal.isEmpty()) {
            cash = belowTotal.get(0);
            if (belowTotal.size() > 1)
                change = belowTotal.get(1);
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
        if (products != null && total != null && cash != null && change != null && subtotal != null) {
            BigDecimal normCash = cash.subtract(change);
            BigDecimal productsSum = Stream.of(products)
                    .map(product -> product.second)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            OcrUtils.log(3, "TicketScheme_" + tag, "productsSum is: " + productsSum);
            OcrUtils.log(3, "TicketScheme_" + tag, "total is: " + total);
            OcrUtils.log(3, "TicketScheme_" + tag, "cash is: " + normCash);
            OcrUtils.log(3, "TicketScheme_" + tag, "subtotal is: " + subtotal);
            if (productsSum.compareTo(total) == 0 && normCash.compareTo(total) == 0 && subtotal.compareTo(total) == 0){
                acceptedList = true;
                return new Scored<>(FOUR_VALUES, new Pair<>(totalText, total));
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
        int THREE_VALUES = 65;
        int THREE_VALUES_AMOUNT = 75;
        int TWO_VALUES_AMOUNT = 55;
        int TWO_VALUES = 30;
        if (total != null) {
            if (products != null && cash != null && subtotal != null && change != null) {
                BigDecimal productsSum = Stream.of(products)
                        .map(product -> product.second)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal normCash = cash.subtract(change);
                OcrUtils.log(3, "TicketScheme_" + tag, "productsSum is: " + productsSum);
                OcrUtils.log(3, "TicketScheme_" + tag, "total is: " + total);
                OcrUtils.log(3, "TicketScheme_" + tag, "cash(n) is: " + normCash);
                OcrUtils.log(3, "TicketScheme_" + tag, "subtotal is: " + subtotal);
                boolean cashPrices = normCash.compareTo(productsSum) == 0;
                boolean cashAmount = normCash.compareTo(total) == 0;
                boolean pricesAmount = productsSum.compareTo(total) == 0;
                boolean subtotalAmount = subtotal.compareTo(total) == 0;
                boolean subtotalCash = subtotal.compareTo(normCash) == 0;
                boolean subtotalPrices = subtotal.compareTo(productsSum) == 0;
                if (cashPrices && pricesAmount) {
                    if (subtotalAmount) {
                        acceptedList = true;
                        return new Scored<>(FOUR_VALUES, new Pair<>(totalText, total));
                    } else {
                        acceptedList = true;
                        return new Scored<>(THREE_VALUES_AMOUNT, new Pair<>(totalText, total));
                    }
                } else if (cashAmount && subtotalAmount) {
                    return new Scored<>(THREE_VALUES_AMOUNT, new Pair<>(totalText, total));
                } else if (pricesAmount && subtotalAmount) {
                    acceptedList = true;
                    return new Scored<>(THREE_VALUES_AMOUNT, new Pair<>(totalText, total));
                } else if (cashPrices && subtotalPrices) {
                    acceptedList = true;
                    return new Scored<>(THREE_VALUES, new Pair<>(null, subtotal));
                } else if (cashAmount || subtotalAmount) {
                    return new Scored<>(TWO_VALUES_AMOUNT, new Pair<>(totalText, total));
                } else if (pricesAmount) {
                    acceptedList = true;
                    return new Scored<>(TWO_VALUES_AMOUNT, new Pair<>(totalText, total));
                } else if (cashPrices || subtotalPrices) {
                    acceptedList = true;
                    return new Scored<>(TWO_VALUES, new Pair<>(null, productsSum));
                } else if (subtotalCash) {
                    return new Scored<>(TWO_VALUES, new Pair<>(null, subtotal));
                } else {
                    return new Scored<>(NO_MATCH, new Pair<>(totalText, total));
                }
            } else if (products != null && cash != null && change != null) {
                BigDecimal productsSum = Stream.of(products)
                        .map(product -> product.second)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal normCash = cash.subtract(change);
                OcrUtils.log(3, "TicketScheme_" + tag, "productsSum is: " + productsSum);
                OcrUtils.log(3, "TicketScheme_" + tag, "total is: " + total);
                OcrUtils.log(3, "TicketScheme_" + tag, "cash(n) is: " + cash);
                boolean cashPrices = normCash.compareTo(productsSum) == 0;
                boolean cashAmount = normCash.compareTo(total) == 0;
                boolean pricesAmount = productsSum.compareTo(total) == 0;
                if (cashPrices || pricesAmount) {
                    acceptedList = true;
                    if (cashAmount) {
                        return new Scored<>(THREE_VALUES, new Pair<>(totalText, total));
                    } else if (pricesAmount) {
                        return new Scored<>(TWO_VALUES_AMOUNT, new Pair<>(totalText, total));
                    } else {
                        return new Scored<>(TWO_VALUES, new Pair<>(null, normCash));
                    }
                } else {
                    return new Scored<>(NO_MATCH, new Pair<>(totalText, total));
                }
            } else if (products != null && subtotal != null) {
                BigDecimal productsSum = Stream.of(products)
                        .map(product -> product.second)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                OcrUtils.log(3, "TicketScheme_" + tag, "productsSum is: " + productsSum);
                OcrUtils.log(3, "TicketScheme_" + tag, "total is: " + total);
                OcrUtils.log(3, "TicketScheme_" + tag, "subtotal is: " + subtotal);
                boolean pricesAmount = productsSum.compareTo(total) == 0;
                boolean subtotalAmount = subtotal.compareTo(total) == 0;
                boolean subtotalPrices = subtotal.compareTo(productsSum) == 0;
                if (subtotalPrices || pricesAmount) {
                    acceptedList = true;
                    if (subtotalAmount) {
                        return new Scored<>(THREE_VALUES, new Pair<>(totalText, total));
                    } else if (pricesAmount) {
                        return new Scored<>(TWO_VALUES_AMOUNT, new Pair<>(totalText, total));
                    } else {
                        return new Scored<>(TWO_VALUES, new Pair<>(null, subtotal));
                    }
                } else {
                    return new Scored<>(NO_MATCH, new Pair<>(totalText, total));
                }
            } else if (cash != null && subtotal != null && change != null) {
                BigDecimal normCash = cash.subtract(change);
                OcrUtils.log(3, "TicketScheme_" + tag, "total is: " + total);
                OcrUtils.log(3, "TicketScheme_" + tag, "cash(n) is: " + normCash);
                OcrUtils.log(3, "TicketScheme_" + tag, "subtotal is: " + subtotal);
                boolean cashAmount = normCash.compareTo(total) == 0;
                boolean subtotalAmount = subtotal.compareTo(total) == 0;
                boolean subtotalCash = subtotal.compareTo(normCash) == 0;
                if (subtotalCash || cashAmount) {
                    if (subtotalAmount) {
                        return new Scored<>(THREE_VALUES, new Pair<>(totalText, total));
                    } else if (cashAmount) {
                        return new Scored<>(TWO_VALUES_AMOUNT, new Pair<>(totalText, total));
                    } else {
                        return new Scored<>(TWO_VALUES, new Pair<>(null, subtotal));
                    }
                } else {
                    return new Scored<>(NO_MATCH, new Pair<>(totalText, total));
                }
            } else {
                return new Scored<>(NO_MATCH, new Pair<>(totalText, total));
            }
        } else {
            //no total
            if (products != null && cash != null && subtotal != null && change != null) {
                BigDecimal productsSum = Stream.of(products)
                        .map(product -> product.second)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal normCash = cash.subtract(change);
                OcrUtils.log(3, "TicketScheme_" + tag, "productsSum is: " + productsSum);
                OcrUtils.log(3, "TicketScheme_" + tag, "total is: null");
                OcrUtils.log(3, "TicketScheme_" + tag, "cash(n) is: " + normCash);
                OcrUtils.log(3, "TicketScheme_" + tag, "subtotal is: " + subtotal);
                boolean cashPrices = normCash.compareTo(productsSum) == 0;
                boolean subtotalCash = subtotal.compareTo(normCash) == 0;
                boolean subtotalPrices = subtotal.compareTo(productsSum) == 0;
                if (cashPrices && subtotalCash) {
                    acceptedList = true;
                    return new Scored<>(THREE_VALUES, new Pair<>(null, normCash));
                } else if (cashPrices || subtotalPrices) {
                    acceptedList = true;
                    return new Scored<>(TWO_VALUES, new Pair<>(null, productsSum));
                } else if (subtotalCash) {
                    return new Scored<>(TWO_VALUES, new Pair<>(null, subtotal));
                }
            } else if (products != null && cash != null && change != null) {
                BigDecimal productsSum = Stream.of(products)
                        .map(product -> product.second)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal normCash = cash.subtract(change);
                OcrUtils.log(3, "TicketScheme_" + tag, "productsSum is: " + productsSum);
                OcrUtils.log(3, "TicketScheme_" + tag, "total is: null");
                OcrUtils.log(3, "TicketScheme_" + tag, "cash(n) is: " + normCash);
                boolean cashPrices = normCash.compareTo(productsSum) == 0;
                if (cashPrices) {
                    acceptedList = true;
                    return new Scored<>(TWO_VALUES, new Pair<>(null, normCash));
                }
            } else if (products != null && subtotal != null) {
                BigDecimal productsSum = Stream.of(products)
                        .map(product -> product.second)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                OcrUtils.log(3, "TicketScheme_" + tag, "productsSum is: " + productsSum);
                OcrUtils.log(3, "TicketScheme_" + tag, "total is: null");
                OcrUtils.log(3, "TicketScheme_" + tag, "subtotal is: " + subtotal);
                boolean subtotalPrices = subtotal.compareTo(productsSum) == 0;
                if (subtotalPrices) {
                    acceptedList = true;
                    return new Scored<>(TWO_VALUES, new Pair<>(null, subtotal));
                }
            } else if (cash != null && subtotal != null && change != null) {
                BigDecimal normCash = cash.subtract(change);
                OcrUtils.log(3, "TicketScheme_" + tag, "total is: null");
                OcrUtils.log(3, "TicketScheme_" + tag, "cash(n) is: " + normCash);
                OcrUtils.log(3, "TicketScheme_" + tag, "subtotal is: " + subtotal);
                boolean subtotalCash = subtotal.compareTo(normCash) == 0;
                if (subtotalCash) {
                    return new Scored<>(TWO_VALUES, new Pair<>(null, subtotal));
                }
            }
            return null;
        }
    }
}
