package dev.frenkli.ledger.service;

import java.util.UUID;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(UUID transactionId) {
        super("Transaction " + transactionId + " does not exist");
    }
}
