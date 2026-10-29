package dev.frenkli.ledger.service;

/** The API maps this to 409 Conflict. */
public class InstrumentAlreadyExistsException extends RuntimeException {

    public InstrumentAlreadyExistsException(String symbol) {
        super("Instrument " + symbol + " already exists");
    }
}
