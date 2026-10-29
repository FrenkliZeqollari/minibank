package dev.frenkli.ledger.api;

import dev.frenkli.ledger.service.AccountNotFoundException;
import dev.frenkli.ledger.service.BusinessRuleException;
import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns errors into RFC 7807 Problem Details ({@code application/problem+json}). Extending
 * ResponseEntityExceptionHandler does the same for Spring's own errors, e.g. a 400 for a request
 * body that fails Bean Validation.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail accountNotFound(AccountNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Account not found");
        problem.setType(URI.create("urn:problem:ledger:account-not-found"));
        return problem;
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail businessRuleViolated(BusinessRuleException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle(e.getRule().title());
        String code = e.getRule().name().toLowerCase(Locale.ROOT).replace('_', '-');
        problem.setType(URI.create("urn:problem:ledger:" + code));
        return problem;
    }
}
