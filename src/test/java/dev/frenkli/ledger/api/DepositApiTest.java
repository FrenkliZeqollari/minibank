package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.frenkli.ledger.domain.SystemAccounts;
import dev.frenkli.ledger.domain.TransactionType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class DepositApiTest extends AbstractApiTest {

    @Test
    void depositBooksMoneyFromExternalFundingToTheCustomer() {
        UUID account = createAccount("Alice");

        ResponseEntity<TransactionResponse> response = postWithNewKey("/deposits",
                Map.of("accountId", account, "amount", 10_000, "currency", "EUR", "description", "Salary"),
                TransactionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TransactionResponse tx = response.getBody();
        assertThat(tx.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(tx.description()).isEqualTo("Salary");
        assertThat(tx.entries()).containsExactlyInAnyOrder(
                new TransactionResponse.EntryResponse(SystemAccounts.EXTERNAL_FUNDING_ID, -10_000),
                new TransactionResponse.EntryResponse(account, 10_000));
        assertThat(balanceOf(account)).isEqualTo(10_000);
        assertThat(ledgerTotal()).isZero();
    }

    @Test
    void depositsAddUp() {
        UUID account = createAccount("Alice");

        deposit(account, 150);
        deposit(account, 250);

        assertThat(balanceOf(account)).isEqualTo(400);
    }

    @Test
    void depositToUnknownAccountIs404() {
        ResponseEntity<Map> response = postWithNewKey("/deposits",
                Map.of("accountId", UUID.randomUUID(), "amount", 100, "currency", "EUR"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void depositOfZeroIs422() {
        UUID account = createAccount("Alice");

        ResponseEntity<Map> response = postWithNewKey("/deposits",
                Map.of("accountId", account, "amount", 0, "currency", "EUR"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:non-positive-amount");
        assertThat(balanceOf(account)).isZero();
    }

    @Test
    void depositInOtherCurrencyIs422() {
        UUID account = createAccount("Alice");

        ResponseEntity<Map> response = postWithNewKey("/deposits",
                Map.of("accountId", account, "amount", 100, "currency", "USD"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void depositIntoTheSystemAccountIs422() {
        ResponseEntity<Map> response = postWithNewKey("/deposits",
                Map.of("accountId", SystemAccounts.EXTERNAL_FUNDING_ID, "amount", 100, "currency", "EUR"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:account-not-usable");
    }

    @Test
    void depositWithoutAmountIs400() {
        UUID account = createAccount("Alice");

        ResponseEntity<Map> response = postWithNewKey("/deposits",
                Map.of("accountId", account, "currency", "EUR"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
