package dev.frenkli.ledger.api;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.domain.AccountStatus;
import dev.frenkli.ledger.domain.AccountType;
import dev.frenkli.ledger.service.AccountWithBalance;
import java.time.Instant;
import java.util.UUID;

/** {@code balance} is in minor units (cents) of {@code currency}. */
public record AccountResponse(
        UUID id,
        String ownerName,
        AccountType type,
        String currency,
        long balance,
        AccountStatus status,
        Instant createdAt) {

    static AccountResponse from(AccountWithBalance accountWithBalance) {
        Account a = accountWithBalance.account();
        return new AccountResponse(a.getId(), a.getOwnerName(), a.getType(), a.getCurrency(),
                accountWithBalance.balance(), a.getStatus(), a.getCreatedAt());
    }
}
