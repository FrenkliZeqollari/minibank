package dev.frenkli.ledger.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI document is generated from the controllers and DTOs at runtime, so it cannot drift
 * from the code. This class only adds the parts that are not visible in any single endpoint.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ledgerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ledger-core API")
                .version("v1")
                .license(new License().name("See LICENSE in the repository"))
                .description("""
                        A double-entry bank ledger.

                        **Conventions**

                        - **Money** is always an integer number of minor units (cents) plus an ISO 4217 \
                        currency code: `2500` with `EUR` is €25.00. Only `EUR` is supported in v1.
                        - **Entries** carry a signed amount: positive = money into the account, \
                        negative = money out of it. The entries of every transaction sum to 0.
                        - **Idempotency:** every money-moving `POST` requires an `Idempotency-Key` header. \
                        Pick a unique value (e.g. a UUID) per operation and send it again unchanged when \
                        retrying. Same key + same request returns the original response \
                        (`Idempotent-Replayed: true`) without booking again; same key + different request \
                        is `422`.
                        - **Errors** use RFC 7807 Problem Details (`application/problem+json`). The `type` \
                        field (e.g. `urn:problem:ledger:insufficient-funds`) is stable and meant for \
                        programs; `title` and `detail` are for humans.
                        """));
    }
}
