package dev.frenkli.ledger.api;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.domain.AccountStatus;
import dev.frenkli.ledger.domain.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** {@code balance} is in minor units (cents) of {@code currency}. */
public record AccountResponse(
        UUID id,
        String ownerName,
        AccountType type,
        String currency,
        @Schema(description = "Balance in minor units (cents). Never negative for CUSTOMER accounts.", example = "7500")
        long balance,
        AccountStatus status,
        Instant createdAt) {

    static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(), a.getOwnerName(), a.getType(), a.getCurrency(),
                a.getBalance(), a.getStatus(), a.getCreatedAt());
    }
}
