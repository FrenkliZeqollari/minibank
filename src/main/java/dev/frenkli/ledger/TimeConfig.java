package dev.frenkli.ledger;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Where the application gets "now" from. Code never calls {@code Instant.now()} directly; it asks
 * this {@link Clock}. That makes time a dependency like any other: tests and the demo data can
 * use a different clock, while production uses the system clock in UTC.
 */
@Configuration
public class TimeConfig {

    @Bean
    @Profile("!demo") // the demo profile brings its own, settable clock (see demo package)
    Clock clock() {
        return Clock.systemUTC();
    }
}
