package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.domain.AccountType;
import dev.frenkli.ledger.repository.AccountRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final Clock clock;

    public AccountService(AccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public Account open(String ownerName, String currency) {
        Currencies.requireSupported(currency);
        return accounts.save(Account.openCustomerAccount(ownerName, currency, clock.instant()));
    }

    /** Customer accounts, oldest first. System accounts are internal and never listed. */
    @Transactional(readOnly = true)
    public Page<Account> listCustomerAccounts(int page, int size) {
        return accounts.findByTypeOrderByCreatedAtAscIdAsc(AccountType.CUSTOMER, PageRequest.of(page, size));
    }

    /** The account with its cached balance, which always equals SUM(entries.amount). */
    @Transactional(readOnly = true)
    public Account get(UUID accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
