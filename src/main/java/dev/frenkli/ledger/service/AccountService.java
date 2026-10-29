package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.repository.AccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public Account open(String ownerName, String currency) {
        Currencies.requireSupported(currency);
        return accounts.save(Account.openCustomerAccount(ownerName, currency));
    }

    /** The account with its cached balance, which always equals SUM(entries.amount). */
    @Transactional(readOnly = true)
    public Account get(UUID accountId) {
        return accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }
}
