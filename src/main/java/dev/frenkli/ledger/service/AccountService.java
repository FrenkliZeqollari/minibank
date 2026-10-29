package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.repository.AccountRepository;
import dev.frenkli.ledger.repository.EntryRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final EntryRepository entries;

    public AccountService(AccountRepository accounts, EntryRepository entries) {
        this.accounts = accounts;
        this.entries = entries;
    }

    @Transactional
    public AccountWithBalance open(String ownerName, String currency) {
        Currencies.requireSupported(currency);
        Account account = accounts.save(Account.openCustomerAccount(ownerName, currency));
        return new AccountWithBalance(account, 0);
    }

    @Transactional(readOnly = true)
    public AccountWithBalance get(UUID accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        // Balance = SUM(entries.amount). Milestone 2 adds a cached balance column for speed.
        return new AccountWithBalance(account, entries.sumAmountByAccountId(accountId));
    }
}
