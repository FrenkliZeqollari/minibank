package dev.frenkli.ledger.domain;

public enum AccountType {
    /** Belongs to a customer. May never go below 0 (no overdrafts). */
    CUSTOMER,
    /** Belongs to the bank itself, e.g. EXTERNAL_FUNDING. May go negative. */
    SYSTEM
}
