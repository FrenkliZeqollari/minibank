package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Entry;
import dev.frenkli.ledger.domain.LedgerTransaction;
import dev.frenkli.ledger.domain.Posting;
import dev.frenkli.ledger.domain.TransactionType;
import dev.frenkli.ledger.repository.AccountRepository;
import dev.frenkli.ledger.repository.EntryRepository;
import dev.frenkli.ledger.repository.LedgerTransactionRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The double-entry core: the only place that writes transactions and entries. */
@Service
public class LedgerService {

    private final LedgerTransactionRepository transactions;
    private final EntryRepository entries;
    private final AccountRepository accounts;

    public LedgerService(LedgerTransactionRepository transactions, EntryRepository entries,
                         AccountRepository accounts) {
        this.transactions = transactions;
        this.entries = entries;
        this.accounts = accounts;
    }

    /**
     * Records a balanced transaction and updates the cached balances of the accounts involved.
     * MANDATORY propagation: the caller must already have a DB transaction open, so its business
     * checks, the entries and the balance updates commit or roll back together. That is what
     * keeps the cached balance equal to the sum of the entries.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public RecordedTransaction record(TransactionType type, String description, String idempotencyKey,
                                      String requestHash, List<Posting> postings) {
        DoubleEntry.requireBalanced(postings);

        LedgerTransaction transaction = transactions.save(
                new LedgerTransaction(type, description, idempotencyKey, requestHash));
        List<Entry> saved = postings.stream()
                .map(p -> new Entry(transaction.getId(), p.accountId(), p.amount(), transaction.getCreatedAt()))
                .map(entries::save)
                .toList();

        // Each UPDATE locks its account row until commit. Updating in the same order (by account
        // id) in every transaction means two transactions never wait for each other in a circle
        // (A then B vs. B then A), which would be a deadlock. Same order as
        // MoneyMovementService.lockInIdOrder, which normally already holds these locks.
        postings.stream()
                .sorted(Comparator.comparing(Posting::accountId))
                .forEach(p -> {
                    if (accounts.addToBalance(p.accountId(), p.amount()) != 1) {
                        throw new AccountNotFoundException(p.accountId());
                    }
                });
        return new RecordedTransaction(transaction, saved);
    }
}
