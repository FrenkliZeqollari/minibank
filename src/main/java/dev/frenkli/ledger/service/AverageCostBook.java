package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.TradeSide;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cost basis and realized profit with the <b>average cost</b> method, computed by replaying an
 * account's trades in the order they happened. Integer cents only.
 *
 * <p>Average cost: all shares of one instrument cost the same on average. Buying adds the price
 * paid to the cost basis. Selling n of q shares removes n/q of the cost basis; the difference
 * between what the sale brought in and that removed cost is the realized profit (or loss).
 *
 * <p>Rounding: n/q of an amount in cents is rarely a whole number of cents. The removed cost is
 * rounded down, so the rounding remainder stays with the shares still held, and when the last
 * share is sold, all remaining cost goes with it. No cent is ever lost or created:
 * the cost of all shares sold plus the cost basis of the shares held always equals the total
 * paid for all shares bought.
 */
final class AverageCostBook {

    /** One trade as the calculation needs it. {@code grossAmount} = quantity × price, in cents. */
    record TradeEvent(String symbol, TradeSide side, long quantity, long grossAmount) {
    }

    /**
     * @param quantity    shares held now
     * @param costBasis   what the shares held now cost, in cents
     * @param realizedPnl profit (positive) or loss (negative) from sales so far, in cents
     */
    record Holding(String symbol, long quantity, long costBasis, long realizedPnl) {
    }

    private AverageCostBook() {
    }

    /** Replays the trades (oldest first) and returns one holding per symbol, in first-trade order. */
    static Map<String, Holding> replay(List<TradeEvent> trades) {
        Map<String, Holding> holdings = new LinkedHashMap<>();
        for (TradeEvent trade : trades) {
            Holding before = holdings.getOrDefault(trade.symbol(), new Holding(trade.symbol(), 0, 0, 0));
            holdings.put(trade.symbol(), apply(before, trade));
        }
        return holdings;
    }

    private static Holding apply(Holding h, TradeEvent trade) {
        if (trade.side() == TradeSide.BUY) {
            return new Holding(h.symbol(), Math.addExact(h.quantity(), trade.quantity()),
                    Math.addExact(h.costBasis(), trade.grossAmount()), h.realizedPnl());
        }
        if (trade.quantity() > h.quantity()) {
            throw new IllegalStateException("Sold " + trade.quantity() + " " + trade.symbol()
                    + " while holding only " + h.quantity());
        }
        long costOfSold = trade.quantity() == h.quantity()
                ? h.costBasis() // last shares: all remaining cost, including rounding remainders
                : Math.multiplyExact(h.costBasis(), trade.quantity()) / h.quantity(); // rounded down
        return new Holding(h.symbol(), h.quantity() - trade.quantity(), h.costBasis() - costOfSold,
                Math.addExact(h.realizedPnl(), trade.grossAmount() - costOfSold));
    }
}
