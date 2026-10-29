package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.frenkli.ledger.domain.TransactionType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class StatementApiTest extends AbstractApiTest {

    @Test
    void statementListsEntriesNewestFirst() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        rest.postForEntity("/deposits",
                Map.of("accountId", alice, "amount", 10_000, "currency", "EUR", "description", "Salary"),
                String.class);
        rest.postForEntity("/transfers",
                Map.of("fromAccountId", alice, "toAccountId", bob, "amount", 2_500, "currency", "EUR",
                        "description", "Rent"),
                String.class);

        StatementResponse statement = rest.getForObject("/accounts/" + alice + "/entries", StatementResponse.class);

        assertThat(statement.accountId()).isEqualTo(alice);
        assertThat(statement.totalEntries()).isEqualTo(2);
        assertThat(statement.entries()).extracting(StatementResponse.Line::amount).containsExactly(-2_500L, 10_000L);
        assertThat(statement.entries()).extracting(StatementResponse.Line::transactionType)
                .containsExactly(TransactionType.TRANSFER, TransactionType.DEPOSIT);
        assertThat(statement.entries()).extracting(StatementResponse.Line::description)
                .containsExactly("Rent", "Salary");
    }

    @Test
    void statementIsPaged() {
        UUID alice = createAccount("Alice");
        for (int amount = 1; amount <= 5; amount++) {
            deposit(alice, amount);
        }

        StatementResponse first = rest.getForObject("/accounts/" + alice + "/entries?page=0&size=2", StatementResponse.class);
        StatementResponse second = rest.getForObject("/accounts/" + alice + "/entries?page=1&size=2", StatementResponse.class);
        StatementResponse last = rest.getForObject("/accounts/" + alice + "/entries?page=2&size=2", StatementResponse.class);

        assertThat(first.totalEntries()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.entries()).extracting(StatementResponse.Line::amount).containsExactly(5L, 4L);
        assertThat(second.entries()).extracting(StatementResponse.Line::amount).containsExactly(3L, 2L);
        assertThat(last.entries()).extracting(StatementResponse.Line::amount).containsExactly(1L);
    }

    @Test
    void newAccountHasAnEmptyStatement() {
        UUID alice = createAccount("Alice");

        StatementResponse statement = rest.getForObject("/accounts/" + alice + "/entries", StatementResponse.class);

        assertThat(statement.entries()).isEmpty();
        assertThat(statement.totalEntries()).isZero();
    }

    @Test
    void unknownAccountIs404() {
        ResponseEntity<Map> response = rest.getForEntity("/accounts/" + UUID.randomUUID() + "/entries", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void pageSizeAbove100Is400() {
        UUID alice = createAccount("Alice");

        ResponseEntity<Map> response = rest.getForEntity("/accounts/" + alice + "/entries?size=101", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void negativePageIs400() {
        UUID alice = createAccount("Alice");

        ResponseEntity<Map> response = rest.getForEntity("/accounts/" + alice + "/entries?page=-1", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
