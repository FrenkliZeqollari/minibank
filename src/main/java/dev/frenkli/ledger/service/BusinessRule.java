package dev.frenkli.ledger.service;

/** Business rules a request can break. The API maps all of them to 422 Unprocessable Entity. */
public enum BusinessRule {
    NON_POSITIVE_AMOUNT("Amount must be positive"),
    UNSUPPORTED_CURRENCY("Currency not supported"),
    CURRENCY_MISMATCH("Currency mismatch"),
    SAME_ACCOUNT_TRANSFER("Transfer to the same account"),
    INSUFFICIENT_FUNDS("Insufficient funds"),
    ACCOUNT_NOT_USABLE("Account cannot be used"),
    IDEMPOTENCY_KEY_REUSED("Idempotency-Key reused for a different request");

    private final String title;

    BusinessRule(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
