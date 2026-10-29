package dev.frenkli.ledger.api;

import dev.frenkli.ledger.service.Portfolio;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** All amounts in minor units (cents) of {@code currency}. */
public record PortfolioResponse(
        UUID accountId,
        String currency,
        @Schema(description = "Cash balance of the account") long cash,
        @Schema(description = "Open positions (shares held now)") List<Position> positions,
        @Schema(description = "Sum of the positions' market values at their latest prices") long marketValue,
        @Schema(description = "What the shares held now cost (average cost method)") long costBasis,
        @Schema(description = "marketValue − costBasis: profit (+) or loss (−) if everything were sold now")
        long unrealizedPnl,
        @Schema(description = "Profit (+) or loss (−) already made by selling, including closed positions")
        long realizedPnl,
        @Schema(description = "cash + marketValue") long totalValue) {

    @Schema(name = "PortfolioPosition")
    public record Position(
            String symbol,
            String name,
            long quantity,
            long costBasis,
            @Schema(description = "Latest market price per share") long lastPrice,
            Instant priceAsOf,
            @Schema(description = "quantity × lastPrice") long marketValue,
            long unrealizedPnl,
            long realizedPnl) {
    }

    static PortfolioResponse from(Portfolio p) {
        List<Position> positions = p.positions().stream()
                .map(x -> new Position(x.symbol(), x.name(), x.quantity(), x.costBasis(), x.lastPrice(), x.priceAsOf(),
                        x.marketValue(), x.unrealizedPnl(), x.realizedPnl()))
                .toList();
        return new PortfolioResponse(p.accountId(), p.currency(), p.cash(), positions, p.marketValue(), p.costBasis(),
                p.unrealizedPnl(), p.realizedPnl(), p.totalValue());
    }
}
