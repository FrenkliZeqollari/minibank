package dev.frenkli.ledger.service;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID accountId) {
        super("Account " + accountId + " does not exist");
    }
}
