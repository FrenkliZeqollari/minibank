package dev.frenkli.ledger.domain;

public enum TransactionType {
    DEPOSIT,
    TRANSFER,
    REVERSAL,
    /** The cash side of a securities trade (buy or sell). */
    TRADE
}
