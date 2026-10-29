package dev.frenkli.ledger.api;

import dev.frenkli.ledger.domain.Trade;
import dev.frenkli.ledger.domain.TradeSide;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record TradeResponse(
        UUID id,
        UUID accountId,
        String symbol,
        TradeSide side,
        long quantity,
        @Schema(description = "Execution price per share in cents", example = "1234") long price,
        @Schema(description = "quantity × price in cents: the cash that moved", example = "12340") long grossAmount,
        @Schema(description = "The ledger transaction that moved the cash") UUID transactionId,
        Instant createdAt) {

    static TradeResponse from(Trade t) {
        return new TradeResponse(t.getId(), t.getAccountId(), t.getSymbol(), t.getSide(), t.getQuantity(), t.getPrice(),
                t.getGrossAmount(), t.getTransactionId(), t.getCreatedAt());
    }
}
