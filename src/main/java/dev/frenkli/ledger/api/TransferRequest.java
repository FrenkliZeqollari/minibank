package dev.frenkli.ledger.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code amount} is in minor units (cents). See {@link DepositRequest} on validation. */
public record TransferRequest(
        @Schema(description = "The customer account paying") @NotNull UUID fromAccountId,
        @Schema(description = "The customer account receiving; must differ from fromAccountId") @NotNull UUID toAccountId,
        @Schema(description = "Amount in minor units (cents) of the currency: 2500 = €25.00. Must be greater than 0.", example = "2500") @NotNull Long amount,
        @Schema(description = "Must match both accounts' currency", example = "EUR") @NotBlank String currency,
        @Schema(example = "Rent") @Size(max = 255) String description) {
}
