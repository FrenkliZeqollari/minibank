package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Entry;
import dev.frenkli.ledger.domain.LedgerTransaction;
import java.util.List;

public record RecordedTransaction(LedgerTransaction transaction, List<Entry> entries) {
}
