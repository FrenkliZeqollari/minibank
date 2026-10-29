package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PortfolioApiTest extends AbstractApiTest {

    @Test
    void valuesPositionsAtTheLatestPrice() {
        UUID alice = createAccount("Alice");
        deposit(alice, 100_000);
        String acme = createInstrument(1_000);
        trade(alice, "BUY", acme, 10, String.class);   // cost 100.00
        setPrice(acme, 2_000);
        trade(alice, "BUY", acme, 10, String.class);   // cost 200.00, average now 15.00
        setPrice(acme, 1_800);

        PortfolioResponse portfolio = portfolioOf(alice);

        PortfolioResponse.Position position = portfolio.positions().get(0);
        assertThat(position.symbol()).isEqualTo(acme);
        assertThat(position.quantity()).isEqualTo(20);
        assertThat(position.costBasis()).isEqualTo(30_000);
        assertThat(position.lastPrice()).isEqualTo(1_800);
        assertThat(position.marketValue()).isEqualTo(36_000);
        assertThat(position.unrealizedPnl()).isEqualTo(6_000);
        assertThat(portfolio.cash()).isEqualTo(70_000);
        assertThat(portfolio.totalValue()).isEqualTo(70_000 + 36_000);
    }

    @Test
    void sellingTurnsUnrealizedIntoRealizedProfit() {
        UUID alice = createAccount("Alice");
        deposit(alice, 100_000);
        String acme = createInstrument(1_000);
        trade(alice, "BUY", acme, 10, String.class);   // 10 × 10.00
        setPrice(acme, 1_500);
        trade(alice, "SELL", acme, 4, String.class);   // 4 × 15.00 = 60.00 for shares that cost 40.00

        PortfolioResponse portfolio = portfolioOf(alice);

        assertThat(portfolio.realizedPnl()).isEqualTo(2_000);
        assertThat(portfolio.costBasis()).isEqualTo(6_000);
        assertThat(portfolio.marketValue()).isEqualTo(9_000);
        assertThat(portfolio.unrealizedPnl()).isEqualTo(3_000);
        assertThat(portfolio.cash()).isEqualTo(100_000 - 10_000 + 6_000);
    }

    @Test
    void closedPositionsDisappearButTheirProfitIsKept() {
        UUID alice = createAccount("Alice");
        deposit(alice, 10_000);
        String acme = createInstrument(1_000);
        trade(alice, "BUY", acme, 2, String.class);
        setPrice(acme, 900);
        trade(alice, "SELL", acme, 2, String.class);

        PortfolioResponse portfolio = portfolioOf(alice);

        assertThat(portfolio.positions()).isEmpty();
        assertThat(portfolio.realizedPnl()).isEqualTo(-200);
        assertThat(portfolio.totalValue()).isEqualTo(portfolio.cash()).isEqualTo(9_800);
    }

    @Test
    void positionsInSeveralInstruments() {
        UUID alice = createAccount("Alice");
        deposit(alice, 100_000);
        String first = createInstrument(1_000);
        String second = createInstrument(250);
        trade(alice, "BUY", first, 3, String.class);
        trade(alice, "BUY", second, 8, String.class);

        PortfolioResponse portfolio = portfolioOf(alice);

        assertThat(portfolio.positions()).extracting(PortfolioResponse.Position::symbol).containsExactly(first, second);
        assertThat(portfolio.marketValue()).isEqualTo(3_000 + 2_000);
    }

    @Test
    void accountWithoutTradesHasOnlyCash() {
        UUID alice = createAccount("Alice");
        deposit(alice, 500);

        PortfolioResponse portfolio = portfolioOf(alice);

        assertThat(portfolio.positions()).isEmpty();
        assertThat(portfolio.totalValue()).isEqualTo(500);
        assertThat(portfolio.realizedPnl()).isZero();
    }

    @Test
    void unknownAccountIs404() {
        ResponseEntity<Map> response = rest.getForEntity("/accounts/" + UUID.randomUUID() + "/portfolio", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
