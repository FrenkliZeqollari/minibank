package dev.frenkli.ledger.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.frenkli.ledger.domain.Posting;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DoubleEntryTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void acceptsTwoEntriesThatSumToZero() {
        assertThatCode(() -> DoubleEntry.requireBalanced(List.of(new Posting(a, -500), new Posting(b, 500))))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsMoreThanTwoEntriesThatSumToZero() {
        assertThatCode(() -> DoubleEntry.requireBalanced(List.of(
                new Posting(a, -1000), new Posting(b, 600), new Posting(c, 400))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEntriesThatDoNotSumToZero() {
        assertThatThrownBy(() -> DoubleEntry.requireBalanced(List.of(new Posting(a, -500), new Posting(b, 499))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to -1");
    }

    @Test
    void rejectsASingleEntry() {
        assertThatThrownBy(() -> DoubleEntry.requireBalanced(List.of(new Posting(a, 500))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 entries");
    }

    @Test
    void rejectsNoEntries() {
        assertThatThrownBy(() -> DoubleEntry.requireBalanced(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroAmountEntries() {
        assertThatThrownBy(() -> DoubleEntry.requireBalanced(List.of(new Posting(a, 0), new Posting(b, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be 0");
    }

    @Test
    void rejectsASumThatWouldOverflowInsteadOfWrappingAround() {
        // Long.MAX_VALUE + Long.MAX_VALUE + 2 wraps to exactly 0 with plain '+'.
        List<Posting> postings = List.of(
                new Posting(a, Long.MAX_VALUE), new Posting(b, Long.MAX_VALUE), new Posting(c, 2));

        assertThatThrownBy(() -> DoubleEntry.requireBalanced(postings))
                .isInstanceOf(ArithmeticException.class);
    }
}
