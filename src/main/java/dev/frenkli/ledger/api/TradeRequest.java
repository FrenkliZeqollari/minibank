package dev.frenkli.ledger.api;

import dev.frenkli.ledger.domain.TradeSide;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** No price: a trade always executes at the instrument's current market price. */
public record TradeRequest(
        @NotNull TradeSide side,
        @Schema(example = "ACME") @NotBlank String symbol,
        @Schema(description = "Number of whole shares. Must be at least 1.", example = "10") @NotNull Long quantity) {
}
