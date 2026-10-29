package dev.frenkli.ledger.api;

import dev.frenkli.ledger.domain.LedgerTransaction;
import dev.frenkli.ledger.domain.TransactionType;
import dev.frenkli.ledger.service.RecordedTransaction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        TransactionType type,
        String description,
        Instant createdAt,
        List<EntryResponse> entries) {

    /** One entry: signed amount in cents; positive = into the account, negative = out of it. */
    public record EntryResponse(UUID accountId, long amount) {
    }

    static TransactionResponse from(RecordedTransaction recorded) {
        LedgerTransaction t = recorded.transaction();
        List<EntryResponse> entries = recorded.entries().stream()
                .map(e -> new EntryResponse(e.getAccountId(), e.getAmount()))
                .toList();
        return new TransactionResponse(t.getId(), t.getType(), t.getDescription(), t.getCreatedAt(), entries);
    }
}
