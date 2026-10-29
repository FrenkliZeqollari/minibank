package dev.frenkli.ledger.service;

import static dev.frenkli.ledger.domain.TradeSide.BUY;
import static dev.frenkli.ledger.domain.TradeSide.SELL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.frenkli.ledger.domain.TradeSide;
import dev.frenkli.ledger.service.AverageCostBook.Holding;
import dev.frenkli.ledger.service.AverageCostBook.TradeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AverageCostBookTest {

    private static TradeEvent trade(TradeSide side, long quantity, long pricePerShare) {
        return new TradeEvent("ACME", side, quantity, quantity * pricePerShare);
    }

    private static Holding replay(TradeEvent... trades) {
        return AverageCostBook.replay(List.of(trades)).get("ACME");
    }

    @Test
    void buyingAddsToQuantityAndCost() {
        Holding h = replay(trade(BUY, 10, 1_000), trade(BUY, 10, 2_000));

        assertThat(h).isEqualTo(new Holding("ACME", 20, 30_000, 0));
    }

    @Test
    void sellingRemovesTheAverageCostAndRealizesTheDifference() {
        // 20 shares for 300.00 in total: 15.00 each on average. Selling 5 at 20.00 brings in 100.00,
        // removes 5 × 15.00 = 75.00 of cost, and realizes a profit of 25.00.
        Holding h = replay(trade(BUY, 10, 1_000), trade(BUY, 10, 2_000), trade(SELL, 5, 2_000));

        assertThat(h).isEqualTo(new Holding("ACME", 15, 22_500, 2_500));
    }

    @Test
    void sellingBelowCostRealizesALoss() {
        Holding h = replay(trade(BUY, 10, 1_000), trade(SELL, 10, 800));

        assertThat(h).isEqualTo(new Holding("ACME", 0, 0, -2_000));
    }

    @Test
    void roundingRemaindersStayWithTheSharesHeldAndNoCentIsLost() {
        // 3 shares for 4 cents in total: 1.33… cents each, which cannot be paid in cents.
        TradeEvent buyAll = new TradeEvent("ACME", BUY, 3, 4);
        Holding afterFirstSale = replay(buyAll, new TradeEvent("ACME", SELL, 1, 2));
        Holding afterAllSales = replay(buyAll, new TradeEvent("ACME", SELL, 1, 2), new TradeEvent("ACME", SELL, 1, 2),
                new TradeEvent("ACME", SELL, 1, 2));

        // First sale removes floor(4 × 1/3) = 1 cent of cost; the remainder stays with the 2 shares held.
        assertThat(afterFirstSale).isEqualTo(new Holding("ACME", 2, 3, 1));
        // Sold everything for 6 cents, paid 4: exactly 2 cents profit, nothing lost to rounding.
        assertThat(afterAllSales).isEqualTo(new Holding("ACME", 0, 0, 2));
    }

    @Test
    void costIsConservedOverManyRandomTrades() {
        // Invariant: (cost of everything sold) + (cost basis still held) == (everything paid for buys).
        // Equivalently: realized profit + cost basis held == sell proceeds − buy costs + cost basis held.
        Random random = new Random(7);
        List<TradeEvent> trades = new ArrayList<>();
        long held = 0;
        long paid = 0;
        long received = 0;
        for (int i = 0; i < 1_000; i++) {
            if (held == 0 || random.nextBoolean()) {
                long quantity = 1 + random.nextInt(50);
                long gross = quantity * (1 + random.nextInt(10_000));
                trades.add(new TradeEvent("ACME", BUY, quantity, gross));
                held += quantity;
                paid += gross;
            } else {
                long quantity = 1 + random.nextInt((int) held);
                long gross = quantity * (1 + random.nextInt(10_000));
                trades.add(new TradeEvent("ACME", SELL, quantity, gross));
                held -= quantity;
                received += gross;
            }
            Holding h = AverageCostBook.replay(trades).get("ACME");
            long costOfSold = paid - h.costBasis();
            assertThat(h.quantity()).isEqualTo(held);
            assertThat(h.realizedPnl()).isEqualTo(received - costOfSold);
            assertThat(h.costBasis()).isNotNegative();
            if (h.quantity() == 0) {
                assertThat(h.costBasis()).isZero();
            }
        }
    }

    @Test
    void instrumentsAreTrackedSeparately() {
        Map<String, Holding> holdings = AverageCostBook.replay(List.of(
                new TradeEvent("AAA", BUY, 1, 100), new TradeEvent("BBB", BUY, 2, 400), new TradeEvent("AAA", SELL, 1, 150)));

        assertThat(holdings.get("AAA")).isEqualTo(new Holding("AAA", 0, 0, 50));
        assertThat(holdings.get("BBB")).isEqualTo(new Holding("BBB", 2, 400, 0));
    }

    @Test
    void sellingMoreThanHeldIsABug() {
        assertThatThrownBy(() -> replay(trade(BUY, 1, 100), trade(SELL, 2, 100)))
                .isInstanceOf(IllegalStateException.class);
    }
}
