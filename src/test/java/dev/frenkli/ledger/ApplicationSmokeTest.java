package dev.frenkli.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class ApplicationSmokeTest extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void actuatorHealthIsUp() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void runsAgainstRealPostgres16() {
        String version = jdbc.queryForObject("SHOW server_version", String.class);

        assertThat(version).startsWith("16.");
    }

    @Test
    void flywayHasCreatedItsHistoryTable() {
        Integer tables = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'flyway_schema_history'",
                Integer.class);

        assertThat(tables).isEqualTo(1);
    }
}
