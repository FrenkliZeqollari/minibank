package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.frenkli.ledger.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The OpenAPI document is generated from the code. These tests pin the parts of the contract
 * that clients rely on, so a refactoring cannot silently drop them from the docs.
 */
class OpenApiDocsTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    private JsonNode spec;

    @BeforeEach
    void loadSpec() throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        spec = objectMapper.readTree(response.getBody());
    }

    @Test
    void documentsEveryEndpointAndNothingElse() {
        List<String> operations = new ArrayList<>();
        spec.get("paths").properties().forEach(path -> path.getValue().properties()
                .forEach(op -> operations.add(op.getKey().toUpperCase() + " " + path.getKey())));

        assertThat(operations).containsExactlyInAnyOrder(
                "POST /accounts",
                "GET /accounts/{id}",
                "GET /accounts/{id}/entries",
                "POST /deposits",
                "POST /transfers",
                "POST /transactions/{id}/reversal",
                "GET /ledger/health",
                "POST /instruments",
                "GET /instruments",
                "POST /instruments/{symbol}/prices",
                "POST /accounts/{id}/trades",
                "GET /accounts/{id}/portfolio");
    }

    @Test
    void moneyMovingEndpointsRequireTheIdempotencyKeyHeader() {
        for (String path : List.of("/deposits", "/transfers", "/transactions/{id}/reversal", "/accounts/{id}/trades")) {
            JsonNode post = spec.at("/paths/" + escape(path) + "/post");
            JsonNode header = findParameter(post, "Idempotency-Key");

            assertThat(header).as(path).isNotNull();
            assertThat(header.get("in").asText()).isEqualTo("header");
            assertThat(header.get("required").asBoolean()).isTrue();
            assertThat(post.at("/responses/201/headers/Idempotent-Replayed").isMissingNode()).as(path).isFalse();
        }
    }

    @Test
    void errorsAreDocumentedAsProblemDetails() {
        JsonNode reversal = spec.at("/paths/" + escape("/transactions/{id}/reversal") + "/post/responses");

        assertThat(reversal.has("404")).isTrue();
        assertThat(reversal.has("409")).isTrue();
        assertThat(reversal.has("422")).isTrue();
        assertThat(reversal.at("/409/content").has("application/problem+json")).isTrue();
    }

    @Test
    void successBodiesAreDocumentedAsJson() {
        JsonNode created = spec.at("/paths/~1transfers/post/responses/201/content");

        assertThat(created.has("application/json")).isTrue();
        assertThat(created.at("/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/TransactionResponse");
    }

    @Test
    void amountsAreDocumentedAsIntegerCents() {
        JsonNode amount = spec.at("/components/schemas/TransferRequest/properties/amount");

        assertThat(amount.get("type").asText()).isEqualTo("integer");
        assertThat(amount.get("format").asText()).isEqualTo("int64");
        assertThat(amount.get("description").asText()).contains("cents");
    }

    @Test
    void swaggerUiIsServed() {
        ResponseEntity<String> response = rest.getForEntity("/swagger-ui/index.html", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Swagger UI");
    }

    /** JSON Pointer escaping: '/' inside a key is written as '~1'. */
    private static String escape(String path) {
        return path.replace("~", "~0").replace("/", "~1");
    }

    private static JsonNode findParameter(JsonNode operation, String name) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (parameter.get("name").asText().equals(name)) {
                return parameter;
            }
        }
        return null;
    }
}
