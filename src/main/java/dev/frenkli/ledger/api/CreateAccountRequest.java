package dev.frenkli.ledger.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @Schema(example = "Alice") @NotBlank @Size(max = 255) String ownerName,
        @Schema(description = "ISO 4217 currency code. v1 supports EUR only.", example = "EUR") @NotBlank String currency) {
}
