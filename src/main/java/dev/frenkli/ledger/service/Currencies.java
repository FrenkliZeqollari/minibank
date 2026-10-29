package dev.frenkli.ledger.service;

import java.util.Set;

final class Currencies {

    /** v1 only handles euros. Currency is stored on every account anyway, to allow more later. */
    static final Set<String> SUPPORTED = Set.of("EUR");

    private Currencies() {
    }

    static void requireSupported(String currency) {
        if (!SUPPORTED.contains(currency)) {
            throw new BusinessRuleException(BusinessRule.UNSUPPORTED_CURRENCY,
                    "Currency " + currency + " is not supported, supported: " + SUPPORTED);
        }
    }
}
