package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Entry;
import dev.frenkli.ledger.domain.LedgerTransaction;
import dev.frenkli.ledger.domain.Posting;
import dev.frenkli.ledger.domain.TransactionType;
import dev.frenkli.ledger.repository.EntryRepository;
import dev.frenkli.ledger.repository.LedgerTransactionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The double-entry core: the only place that writes transactions and entries. */
@Service
public class LedgerService {

    private final LedgerTransactionRepository transactions;
    private final EntryRepository entries;

    public LedgerService(LedgerTransactionRepository transactions, EntryRepository entries) {
        this.transactions = transactions;
        this.entries = entries;
    }

    /**
     * Records a balanced transaction. MANDATORY propagation: the caller must already have a DB
     * transaction open, so its business checks and these writes commit or roll back together.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RecordedTransaction record(TransactionType type, String description, List<Posting> postings) {
        DoubleEntry.requireBalanced(postings);

        LedgerTransaction transaction = transactions.save(new LedgerTransaction(type, description));
        List<Entry> saved = postings.stream()
                .map(p -> new Entry(transaction.getId(), p.accountId(), p.amount(), transaction.getCreatedAt()))
                .map(entries::save)
                .toList();
        return new RecordedTransaction(transaction, saved);
    }
}
