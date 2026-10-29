package dev.frenkli.ledger.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * {@code amount} is in minor units (cents). Must be present here (400 if missing); whether it is
 * positive is a business rule checked in the service (422).
 */
public record DepositRequest(
        @Schema(description = "The customer account receiving the money") @NotNull UUID accountId,
        @Schema(description = "Amount in minor units (cents) of the currency: 2500 = €25.00. Must be greater than 0.", example = "10000") @NotNull Long amount,
        @Schema(description = "Must match the account's currency", example = "EUR") @NotBlank String currency,
        @Schema(example = "Salary") @Size(max = 255) String description) {
}
