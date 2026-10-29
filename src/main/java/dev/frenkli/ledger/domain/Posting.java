package dev.frenkli.ledger.domain;

import java.util.UUID;

/** An entry that has not been saved yet: book {@code amount} (signed, in cents) on an account. */
public record Posting(UUID accountId, long amount) {
}
