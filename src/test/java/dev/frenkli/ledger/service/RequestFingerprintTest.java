package dev.frenkli.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestFingerprintTest {

    private final UUID from = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID to = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void sameRequestGivesSameFingerprint() {
        assertThat(RequestFingerprint.of("TRANSFER", from, to, 100L, "EUR", "Rent"))
                .isEqualTo(RequestFingerprint.of("TRANSFER", from, to, 100L, "EUR", "Rent"))
                .hasSize(64);
    }

    @Test
    void everyFieldMatters() {
        String original = RequestFingerprint.of("TRANSFER", from, to, 100L, "EUR", "Rent");

        assertThat(RequestFingerprint.of("DEPOSIT", from, to, 100L, "EUR", "Rent")).isNotEqualTo(original);
        assertThat(RequestFingerprint.of("TRANSFER", to, from, 100L, "EUR", "Rent")).isNotEqualTo(original);
        assertThat(RequestFingerprint.of("TRANSFER", from, to, 101L, "EUR", "Rent")).isNotEqualTo(original);
        assertThat(RequestFingerprint.of("TRANSFER", from, to, 100L, "USD", "Rent")).isNotEqualTo(original);
        assertThat(RequestFingerprint.of("TRANSFER", from, to, 100L, "EUR", "rent")).isNotEqualTo(original);
    }

    @Test
    void missingDescriptionDiffersFromEmptyDescription() {
        assertThat(RequestFingerprint.of("DEPOSIT", to, 100L, "EUR", null))
                .isNotEqualTo(RequestFingerprint.of("DEPOSIT", to, 100L, "EUR", ""));
    }

    @Test
    void separatorsInsideValuesCannotFakeAnotherRequest() {
        assertThat(RequestFingerprint.of("X", "a|b", "c"))
                .isNotEqualTo(RequestFingerprint.of("X", "a", "b|c"));
    }
}
