package dev.frenkli.ledger.service;

/**
 * @param replayed true if this is the stored result of an earlier request with the same
 *                 Idempotency-Key, and nothing new was booked
 */
public record MovementResult(RecordedTransaction recorded, boolean replayed) {
}
