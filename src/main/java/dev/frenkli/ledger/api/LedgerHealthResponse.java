package dev.frenkli.ledger.api;

import dev.frenkli.ledger.service.LedgerHealth;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * {@code healthy} is true when the sum of all entries is 0 and no account's cached balance
 * differs from the sum of its entries.
 */
public record LedgerHealthResponse(
        boolean healthy,
        @Schema(description = "Sum of every entry in the ledger, in cents. Must be 0.", example = "0")
        BigDecimal sumOfAllEntries,
        @Schema(description = "Accounts whose cached balance differs from the sum of their entries. Must be empty.")
        List<Mismatch> balanceMismatches) {

    @Schema(name = "BalanceMismatch")
    public record Mismatch(UUID accountId, long cachedBalance, long entriesSum) {
    }

    static LedgerHealthResponse from(LedgerHealth health) {
        List<Mismatch> mismatches = health.balanceMismatches().stream()
                .map(m -> new Mismatch(m.accountId(), m.cachedBalance(), m.entriesSum()))
                .toList();
        return new LedgerHealthResponse(health.healthy(), health.sumOfAllEntries(), mismatches);
    }
}
