package dev.frenkli.ledger.repository;

import dev.frenkli.ledger.domain.TransactionType;
import java.time.Instant;
import java.util.UUID;

/** One line of an account statement: an entry together with the transaction it belongs to. */
public record StatementLine(
        UUID entryId,
        UUID transactionId,
        TransactionType transactionType,
        String description,
        long amount,
        Instant createdAt,
        UUID reversedByTransactionId) {
}
