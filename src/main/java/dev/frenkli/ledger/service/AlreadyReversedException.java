package dev.frenkli.ledger.service;

import java.util.UUID;

/** The transaction was already reversed once. The API maps this to 409 Conflict. */
public class AlreadyReversedException extends RuntimeException {

    public AlreadyReversedException(UUID transactionId) {
        super("Transaction " + transactionId + " has already been reversed");
    }
}
