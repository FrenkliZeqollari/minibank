package dev.frenkli.ledger.api;

import dev.frenkli.ledger.service.AccountService;
import dev.frenkli.ledger.service.StatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
@Tag(name = "Accounts", description = "Customer accounts, balances and statements")
public class AccountController {

    private final AccountService accountService;
    private final StatementService statementService;

    public AccountController(AccountService accountService, StatementService statementService) {
        this.accountService = accountService;
        this.statementService = statementService;
    }

    @PostMapping
    @Operation(summary = "Open a customer account", description = "A new account starts with a balance of 0.")
    @ApiResponse(responseCode = "201", description = "Account opened. The Location header points to it.")
    @ApiResponse(responseCode = "400", description = "Missing or invalid field.", content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Currency not supported (v1: EUR only).", content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse account = AccountResponse.from(
                accountService.open(request.ownerName(), request.currency()));
        return ResponseEntity.created(URI.create("/accounts/" + account.id())).body(account);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an account and its balance")
    @ApiResponse(responseCode = "200", description = "The account.")
    @ApiResponse(responseCode = "404", description = "Unknown account.", content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(accountService.get(id));
    }

    /** The account statement, newest first. {@code page} starts at 0; {@code size} is 1 to 100. */
    @GetMapping("/{id}/entries")
    @Operation(summary = "Get the account statement", description = "The account's entries, newest first, one page "
            + "at a time.")
    @ApiResponse(responseCode = "200", description = "One page of the statement.")
    @ApiResponse(responseCode = "400", description = "page < 0, or size outside 1-100.", content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Unknown account.", content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public StatementResponse entries(@PathVariable UUID id,
                                     @Parameter(description = "Page number, starting at 0")
                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                     @Parameter(description = "Entries per page, 1 to 100")
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return StatementResponse.from(id, statementService.statement(id, page, size));
    }
}
