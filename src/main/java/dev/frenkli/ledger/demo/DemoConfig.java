package dev.frenkli.ledger.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Only active with the "demo" profile. Its settable clock replaces the system clock from
 * TimeConfig: a SettableClock is a java.time.Clock, so the services receive it unchanged.
 */
@Configuration
@Profile("demo")
public class DemoConfig {

    @Bean
    SettableClock clock() {
        return new SettableClock();
    }
}
