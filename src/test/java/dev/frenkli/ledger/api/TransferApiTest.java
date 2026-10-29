package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.frenkli.ledger.domain.TransactionType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TransferApiTest extends AbstractApiTest {

    @Test
    void transferMovesMoneyBetweenCustomers() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 10_000);

        ResponseEntity<TransactionResponse> response = rest.postForEntity("/transfers",
                transfer(alice, bob, 2_500, "EUR"), TransactionResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TransactionResponse tx = response.getBody();
        assertThat(tx.type()).isEqualTo(TransactionType.TRANSFER);
        assertThat(tx.entries()).containsExactlyInAnyOrder(
                new TransactionResponse.EntryResponse(alice, -2_500),
                new TransactionResponse.EntryResponse(bob, 2_500));
        assertThat(balanceOf(alice)).isEqualTo(7_500);
        assertThat(balanceOf(bob)).isEqualTo(2_500);
        assertThat(ledgerTotal()).isZero();
    }

    @Test
    void transferOfTheWholeBalanceIsAllowed() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 1_000);

        ResponseEntity<String> response = rest.postForEntity("/transfers",
                transfer(alice, bob, 1_000, "EUR"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(balanceOf(alice)).isZero();
    }

    @Test
    void insufficientFundsIs422AndNothingIsBooked() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 1_000);

        ResponseEntity<Map> response = rest.postForEntity("/transfers",
                transfer(alice, bob, 1_001, "EUR"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:insufficient-funds");
        assertThat(balanceOf(alice)).isEqualTo(1_000);
        assertThat(balanceOf(bob)).isZero();
    }

    @Test
    void sameAccountTransferIs422() {
        UUID alice = createAccount("Alice");
        deposit(alice, 1_000);

        ResponseEntity<Map> response = rest.postForEntity("/transfers",
                transfer(alice, alice, 100, "EUR"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:same-account-transfer");
    }

    @Test
    void negativeAmountIs422() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(bob, 1_000);

        // A negative transfer from Alice to Bob would really pull money out of Bob's account.
        ResponseEntity<Map> response = rest.postForEntity("/transfers",
                transfer(alice, bob, -500, "EUR"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(balanceOf(bob)).isEqualTo(1_000);
    }

    @Test
    void currencyMismatchIs422() {
        UUID alice = createAccount("Alice");
        UUID bob = createAccount("Bob");
        deposit(alice, 1_000);

        ResponseEntity<Map> response = rest.postForEntity("/transfers",
                transfer(alice, bob, 100, "USD"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void unknownRecipientIs404AndNothingIsBooked() {
        UUID alice = createAccount("Alice");
        deposit(alice, 1_000);

        ResponseEntity<Map> response = rest.postForEntity("/transfers",
                transfer(alice, UUID.randomUUID(), 100, "EUR"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(balanceOf(alice)).isEqualTo(1_000);
    }

    private static Map<String, Object> transfer(UUID from, UUID to, long amount, String currency) {
        Map<String, Object> body = new HashMap<>();
        body.put("fromAccountId", from);
        body.put("toAccountId", to);
        body.put("amount", amount);
        body.put("currency", currency);
        body.put("description", "Rent");
        return body;
    }
}
