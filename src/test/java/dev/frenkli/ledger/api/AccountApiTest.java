package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.frenkli.ledger.domain.AccountStatus;
import dev.frenkli.ledger.domain.AccountType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class AccountApiTest extends AbstractApiTest {

    @Test
    void createsACustomerAccountWithZeroBalance() {
        ResponseEntity<AccountResponse> response = rest.postForEntity("/accounts",
                Map.of("ownerName", "Ada Lovelace", "currency", "EUR"), AccountResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AccountResponse account = response.getBody();
        assertThat(response.getHeaders().getLocation()).hasPath("/accounts/" + account.id());
        assertThat(account.ownerName()).isEqualTo("Ada Lovelace");
        assertThat(account.type()).isEqualTo(AccountType.CUSTOMER);
        assertThat(account.currency()).isEqualTo("EUR");
        assertThat(account.balance()).isZero();
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.createdAt()).isNotNull();
    }

    @Test
    void getsAnAccountWithItsBalance() {
        UUID id = createAccount("Grace Hopper");
        deposit(id, 12_345);

        AccountResponse account = rest.getForObject("/accounts/" + id, AccountResponse.class);

        assertThat(account.id()).isEqualTo(id);
        assertThat(account.balance()).isEqualTo(12_345);
    }

    @Test
    void unknownAccountIs404ProblemDetails() {
        ResponseEntity<Map> response = rest.getForEntity("/accounts/" + UUID.randomUUID(), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).containsEntry("title", "Account not found");
    }

    @Test
    void unsupportedCurrencyIs422() {
        ResponseEntity<Map> response = rest.postForEntity("/accounts",
                Map.of("ownerName", "Bob", "currency", "USD"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("type", "urn:problem:ledger:unsupported-currency");
    }

    @Test
    void missingOwnerNameIs400ProblemDetails() {
        ResponseEntity<Map> response = rest.postForEntity("/accounts", Map.of("currency", "EUR"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
