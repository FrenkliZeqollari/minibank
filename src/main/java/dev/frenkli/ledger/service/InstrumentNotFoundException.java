package dev.frenkli.ledger.service;

public class InstrumentNotFoundException extends RuntimeException {

    public InstrumentNotFoundException(String symbol) {
        super("Instrument " + symbol + " does not exist");
    }
}
