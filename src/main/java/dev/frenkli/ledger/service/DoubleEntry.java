package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Posting;
import java.util.List;

/**
 * The double-entry rule: a transaction has at least 2 entries and they sum to exactly 0, so money
 * is only ever moved between accounts, never created or destroyed.
 *
 * <p>This is the service-layer check. The database checks the same rule again at commit
 * (constraint triggers in V1__ledger_schema.sql), in case a bug ever gets past this one.
 */
final class DoubleEntry {

    private DoubleEntry() {
    }

    static void requireBalanced(List<Posting> postings) {
        if (postings.size() < 2) {
            throw new IllegalArgumentException(
                    "A transaction needs at least 2 entries, got " + postings.size());
        }
        long sum = 0;
        for (Posting posting : postings) {
            if (posting.amount() == 0) {
                throw new IllegalArgumentException("An entry amount must not be 0");
            }
            // addExact throws on overflow instead of silently wrapping around: with plain '+',
            // two huge amounts could wrap to a sum that looks like 0.
            sum = Math.addExact(sum, posting.amount());
        }
        if (sum != 0) {
            throw new IllegalArgumentException("Entries must sum to 0, but sum to " + sum);
        }
    }
}
