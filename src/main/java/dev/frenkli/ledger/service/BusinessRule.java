package dev.frenkli.ledger.service;

/** Business rules a request can break. The API maps all of them to 422 Unprocessable Entity. */
public enum BusinessRule {
    NON_POSITIVE_AMOUNT("Amount must be positive"),
    UNSUPPORTED_CURRENCY("Currency not supported"),
    CURRENCY_MISMATCH("Currency mismatch"),
    SAME_ACCOUNT_TRANSFER("Transfer to the same account"),
    INSUFFICIENT_FUNDS("Insufficient funds"),
    ACCOUNT_NOT_USABLE("Account cannot be used"),
    IDEMPOTENCY_KEY_REUSED("Idempotency-Key reused for a different request"),
    REVERSAL_NOT_REVERSIBLE("A reversal cannot be reversed"),
    TRADE_NOT_REVERSIBLE("A trade cannot be reversed"),
    NON_POSITIVE_QUANTITY("Quantity must be positive"),
    INSUFFICIENT_HOLDINGS("Insufficient holdings"),
    NO_MARKET_PRICE("No market price"),
    AMOUNT_TOO_LARGE("Amount too large");

    private final String title;

    BusinessRule(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
