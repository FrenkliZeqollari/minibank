package dev.frenkli.ledger.service;

import dev.frenkli.ledger.repository.BalanceMismatch;
import java.math.BigDecimal;
import java.util.List;

/**
 * @param sumOfAllEntries   must be 0: money only moves between accounts
 * @param balanceMismatches must be empty: every cached balance equals the sum of its entries
 */
public record LedgerHealth(BigDecimal sumOfAllEntries, List<BalanceMismatch> balanceMismatches) {

    public boolean healthy() {
        return sumOfAllEntries.signum() == 0 && balanceMismatches.isEmpty();
    }
}
