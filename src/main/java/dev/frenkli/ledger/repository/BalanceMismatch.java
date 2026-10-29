package dev.frenkli.ledger.repository;

import java.util.UUID;

/** An account whose cached balance differs from the sum of its entries. Should never exist. */
public record BalanceMismatch(UUID accountId, long cachedBalance, long entriesSum) {
}
