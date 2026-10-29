package dev.frenkli.ledger;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Base class for integration tests: boots the whole application on a random port against the
 * Testcontainers Postgres. Spring caches the context, so all subclasses share one container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {
}
