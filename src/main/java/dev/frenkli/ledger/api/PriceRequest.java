package dev.frenkli.ledger.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record PriceRequest(
        @Schema(description = "Market price per share in minor units (cents): 1234 = €12.34. Must be greater than 0.",
                example = "1234")
        @NotNull Long price) {
}
