package dev.frenkli.ledger.api;

import dev.frenkli.ledger.service.AccountService;
import dev.frenkli.ledger.service.StatementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
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
public class AccountController {

    private final AccountService accountService;
    private final StatementService statementService;

    public AccountController(AccountService accountService, StatementService statementService) {
        this.accountService = accountService;
        this.statementService = statementService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse account = AccountResponse.from(
                accountService.open(request.ownerName(), request.currency()));
        return ResponseEntity.created(URI.create("/accounts/" + account.id())).body(account);
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(accountService.get(id));
    }

    /** The account statement, newest first. {@code page} starts at 0; {@code size} is 1 to 100. */
    @GetMapping("/{id}/entries")
    public StatementResponse entries(@PathVariable UUID id,
                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return StatementResponse.from(id, statementService.statement(id, page, size));
    }
}
