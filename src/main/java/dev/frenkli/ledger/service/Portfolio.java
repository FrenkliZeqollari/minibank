package dev.frenkli.ledger.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An account's portfolio. All amounts are in minor units (cents).
 *
 * @param cash               the account's cash balance
 * @param marketValue        sum of the positions' market values
 * @param costBasis          sum of what the shares held now cost
 * @param unrealizedPnl      marketValue − costBasis: profit or loss if everything were sold now
 * @param realizedPnl        profit or loss already made by selling, including closed positions
 * @param totalValue         cash + marketValue
 */
public record Portfolio(
        UUID accountId,
        String currency,
        long cash,
        List<Position> positions,
        long marketValue,
        long costBasis,
        long unrealizedPnl,
        long realizedPnl,
        long totalValue) {

    /** One instrument the account holds shares of. */
    public record Position(
            String symbol,
            String name,
            long quantity,
            long costBasis,
            long lastPrice,
            Instant priceAsOf,
            long marketValue,
            long unrealizedPnl,
            long realizedPnl) {
    }
}
