package dev.frenkli.ledger.api;

import dev.frenkli.ledger.domain.InstrumentPrice;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record PriceResponse(
        String symbol,
        @Schema(description = "Price per share in cents", example = "1234") long price,
        Instant createdAt) {

    static PriceResponse from(InstrumentPrice price) {
        return new PriceResponse(price.getSymbol(), price.getPrice(), price.getCreatedAt());
    }
}
