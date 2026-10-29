package dev.frenkli.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.frenkli.ledger.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The browser frontend is served by the same application, from src/main/resources/static. */
class FrontendTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void rootServesTheFrontend() {
        ResponseEntity<String> response = rest.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("text/html");
        assertThat(response.getBody()).contains("<title>ledger-core</title>", "js/app.js");
    }

    @Test
    void scriptsAreServedAsJavaScript() {
        // Browsers refuse to run an ES module that is not served with a JavaScript content type.
        for (String path : new String[] {"/js/app.js", "/js/api.js", "/js/money.js"}) {
            ResponseEntity<String> response = rest.getForEntity(path, String.class);

            assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getContentType().toString()).as(path).contains("javascript");
        }
    }

    @Test
    void stylesheetIsServed() {
        ResponseEntity<String> response = rest.getForEntity("/css/app.css", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("text/css");
    }
}
