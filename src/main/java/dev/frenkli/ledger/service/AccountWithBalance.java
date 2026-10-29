package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Account;

/** An account and its balance in cents. */
public record AccountWithBalance(Account account, long balance) {
}
