package dev.frenkli.ledger.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateInstrumentRequest(
        @Schema(description = "1-12 characters: capital letters, digits or dots", example = "ACME")
        @NotBlank @Pattern(regexp = "[A-Z0-9.]{1,12}") String symbol,
        @Schema(example = "ACME Corp.") @NotBlank @Size(max = 255) String name,
        @Schema(description = "ISO 4217 currency code. v1 supports EUR only.", example = "EUR") @NotBlank String currency) {
}
