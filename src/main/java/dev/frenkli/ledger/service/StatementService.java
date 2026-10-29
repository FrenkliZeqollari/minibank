package dev.frenkli.ledger.service;

import dev.frenkli.ledger.repository.AccountRepository;
import dev.frenkli.ledger.repository.EntryRepository;
import dev.frenkli.ledger.repository.StatementLine;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementService {

    private final AccountRepository accounts;
    private final EntryRepository entries;

    public StatementService(AccountRepository accounts, EntryRepository entries) {
        this.accounts = accounts;
        this.entries = entries;
    }

    /** One page of the account's entries, newest first. */
    @Transactional(readOnly = true)
    public Page<StatementLine> statement(UUID accountId, int page, int size) {
        if (!accounts.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }
        return entries.findStatement(accountId, PageRequest.of(page, size));
    }
}
