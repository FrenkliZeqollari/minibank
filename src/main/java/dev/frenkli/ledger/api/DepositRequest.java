package dev.frenkli.ledger.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * {@code amount} is in minor units (cents). Must be present here (400 if missing); whether it is
 * positive is a business rule checked in the service (422).
 */
public record DepositRequest(
        @NotNull UUID accountId,
        @NotNull Long amount,
        @NotBlank String currency,
        @Size(max = 255) String description) {
}
