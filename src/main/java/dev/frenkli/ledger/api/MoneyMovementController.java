package dev.frenkli.ledger.api;

import dev.frenkli.ledger.service.MoneyMovementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MoneyMovementController {

    private final MoneyMovementService moneyMovementService;

    public MoneyMovementController(MoneyMovementService moneyMovementService) {
        this.moneyMovementService = moneyMovementService;
    }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse deposit(@Valid @RequestBody DepositRequest request) {
        return TransactionResponse.from(moneyMovementService.deposit(
                request.accountId(), request.amount(), request.currency(), request.description()));
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse transfer(@Valid @RequestBody TransferRequest request) {
        return TransactionResponse.from(moneyMovementService.transfer(
                request.fromAccountId(), request.toAccountId(), request.amount(),
                request.currency(), request.description()));
    }
}
