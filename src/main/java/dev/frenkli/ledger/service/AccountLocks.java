package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.repository.AccountRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The one locking rule for every money movement (domain rule 8). */
@Component
class AccountLocks {

    private final AccountRepository accounts;

    AccountLocks(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /**
     * Locks the accounts' rows one by one, always in ascending id order, whatever order the
     * request names them in. If one transfer locked A then B while another locked B then A, each
     * could end up holding one lock and waiting forever for the other (a deadlock). With one
     * global order, whoever gets the first lock also gets the second.
     *
     * <p>Must use the same order as the balance updates in {@link LedgerService#record}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Map<UUID, Account> lockInIdOrder(Collection<UUID> accountIds) {
        Map<UUID, Account> locked = new HashMap<>();
        for (UUID id : new TreeSet<>(accountIds)) {
            locked.put(id, accounts.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id)));
        }
        return locked;
    }
}
