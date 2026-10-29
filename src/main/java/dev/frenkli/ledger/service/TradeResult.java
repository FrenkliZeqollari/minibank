package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Trade;

/** @param replayed true if this is the stored trade of an earlier request with the same Idempotency-Key */
public record TradeResult(Trade trade, boolean replayed) {
}
