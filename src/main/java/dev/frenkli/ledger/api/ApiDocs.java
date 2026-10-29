package dev.frenkli.ledger.api;

import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.ProblemDetail;

/**
 * Reusable OpenAPI annotations, so the same documentation is not copied onto every endpoint.
 * Annotations on annotations ("meta-annotations") are understood by springdoc.
 */
final class ApiDocs {

    static final String PROBLEM_JSON = "application/problem+json";

    private ApiDocs() {
    }

    static final String IDEMPOTENCY_KEY_DESCRIPTION = "Unique id for this operation, chosen by the client "
            + "(e.g. a UUID). Send the same value again when retrying: the operation then happens at most once. "
            + "Max 255 characters.";
    static final String IDEMPOTENCY_KEY_EXAMPLE = "9b2d7e41-5f0a-4c3e-8d6b-2a1f4e7c9d30";

    /** 201 for a money-moving POST, including the replay header. */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "201", description = "Booked (or, on a retry, the original booking).",
            headers = @Header(name = "Idempotent-Replayed",
                    description = "true if this is the stored response of an earlier request with the same "
                            + "Idempotency-Key and nothing new was booked",
                    schema = @Schema(type = "boolean")))
    @ApiResponse(responseCode = "400",
            description = "Malformed request: missing field, invalid JSON, or missing/blank Idempotency-Key.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "An account or transaction in the request does not exist.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @interface MoneyMovementResponses {
    }
}
