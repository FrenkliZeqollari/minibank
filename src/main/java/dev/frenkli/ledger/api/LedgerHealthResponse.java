package dev.frenkli.ledger.api;

import dev.frenkli.ledger.service.LedgerHealth;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * {@code healthy} is true when the sum of all entries is 0 and no account's cached balance
 * differs from the sum of its entries.
 */
public record LedgerHealthResponse(
        boolean healthy,
        BigDecimal sumOfAllEntries,
        List<Mismatch> balanceMismatches) {

    public record Mismatch(UUID accountId, long cachedBalance, long entriesSum) {
    }

    static LedgerHealthResponse from(LedgerHealth health) {
        List<Mismatch> mismatches = health.balanceMismatches().stream()
                .map(m -> new Mismatch(m.accountId(), m.cachedBalance(), m.entriesSum()))
                .toList();
        return new LedgerHealthResponse(health.healthy(), health.sumOfAllEntries(), mismatches);
    }
}
