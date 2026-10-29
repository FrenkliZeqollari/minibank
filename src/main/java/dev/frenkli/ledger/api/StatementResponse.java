package dev.frenkli.ledger.api;

import dev.frenkli.ledger.domain.TransactionType;
import dev.frenkli.ledger.repository.StatementLine;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

/** One page of an account statement, newest first. */
public record StatementResponse(
        UUID accountId,
        List<Line> entries,
        int page,
        int size,
        long totalEntries,
        int totalPages) {

    /** {@code amount} is signed cents: positive = money in, negative = money out. */
    @Schema(name = "StatementLine")
    public record Line(
            UUID entryId,
            UUID transactionId,
            TransactionType transactionType,
            String description,
            @Schema(description = "Signed cents: positive = money in, negative = money out", example = "-2500")
            long amount,
            Instant createdAt) {
    }

    static StatementResponse from(UUID accountId, Page<StatementLine> page) {
        List<Line> lines = page.getContent().stream()
                .map(l -> new Line(l.entryId(), l.transactionId(), l.transactionType(), l.description(),
                        l.amount(), l.createdAt()))
                .toList();
        return new StatementResponse(accountId, lines, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
