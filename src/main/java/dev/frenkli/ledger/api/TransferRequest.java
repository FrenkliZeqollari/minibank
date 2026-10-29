package dev.frenkli.ledger.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code amount} is in minor units (cents). See {@link DepositRequest} on validation. */
public record TransferRequest(
        @NotNull UUID fromAccountId,
        @NotNull UUID toAccountId,
        @NotNull Long amount,
        @NotBlank String currency,
        @Size(max = 255) String description) {
}
