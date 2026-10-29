package dev.frenkli.ledger.domain;

import java.util.UUID;

/** Well-known system accounts, seeded by Flyway migration V1. */
public final class SystemAccounts {

    /**
     * Where money comes from when it enters the bank (and goes to when it leaves). Its balance is
     * negative: minus the total that customers have deposited.
     */
    public static final UUID EXTERNAL_FUNDING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private SystemAccounts() {
    }
}
