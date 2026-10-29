package dev.frenkli.ledger.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.frenkli.ledger.domain.LedgerTransaction;
import dev.frenkli.ledger.domain.TransactionType;
import dev.frenkli.ledger.service.RecordedTransaction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        TransactionType type,
        String description,
        // Only present on a REVERSAL; left out of the JSON otherwise.
        @Schema(description = "Only on a REVERSAL: the transaction it cancels")
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID reversesTransactionId,
        Instant createdAt,
        @Schema(description = "Always at least 2; their amounts sum to 0")
        List<EntryResponse> entries) {

    /** One entry: signed amount in cents; positive = into the account, negative = out of it. */
    public record EntryResponse(
            UUID accountId,
            @Schema(description = "Signed cents: positive = into the account, negative = out of it", example = "-2500")
            long amount) {
    }

    static TransactionResponse from(RecordedTransaction recorded) {
        LedgerTransaction t = recorded.transaction();
        // Sorted (money out first), so a replay read back from the database lists the entries in
        // the same order as the original response.
        List<EntryResponse> entries = recorded.entries().stream()
                .map(e -> new EntryResponse(e.getAccountId(), e.getAmount()))
                .sorted(Comparator.comparingLong(EntryResponse::amount).thenComparing(EntryResponse::accountId))
                .toList();
        return new TransactionResponse(t.getId(), t.getType(), t.getDescription(), t.getReversesTransactionId(),
                t.getCreatedAt(), entries);
    }
}
