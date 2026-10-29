package dev.frenkli.ledger.api;

import dev.frenkli.ledger.service.MoneyMovementService;
import dev.frenkli.ledger.service.MovementResult;
import dev.frenkli.ledger.service.ReversalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Money-moving endpoints. Each requires an {@code Idempotency-Key} header (400 if missing): a
 * client-chosen unique id for the operation, sent again unchanged when the client retries.
 */
@RestController
public class MoneyMovementController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    /** Set to "true" when the response is the stored result of an earlier request. */
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final MoneyMovementService moneyMovementService;
    private final ReversalService reversalService;

    public MoneyMovementController(MoneyMovementService moneyMovementService, ReversalService reversalService) {
        this.moneyMovementService = moneyMovementService;
        this.reversalService = reversalService;
    }

    @PostMapping("/deposits")
    public ResponseEntity<TransactionResponse> deposit(
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 255) String idempotencyKey,
            @Valid @RequestBody DepositRequest request) {
        return created(moneyMovementService.deposit(idempotencyKey,
                request.accountId(), request.amount(), request.currency(), request.description()));
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransactionResponse> transfer(
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 255) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return created(moneyMovementService.transfer(idempotencyKey,
                request.fromAccountId(), request.toAccountId(), request.amount(),
                request.currency(), request.description()));
    }

    /** Cancels a transaction by booking its mirror image. A transaction can be reversed once (409). */
    @PostMapping("/transactions/{id}/reversal")
    public ResponseEntity<TransactionResponse> reverse(
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 255) String idempotencyKey,
            @PathVariable UUID id) {
        return created(reversalService.reverse(idempotencyKey, id));
    }

    /** A replay gets the same status and body as the original response. */
    private static ResponseEntity<TransactionResponse> created(MovementResult result) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(IDEMPOTENT_REPLAYED, String.valueOf(result.replayed()))
                .body(TransactionResponse.from(result.recorded()));
    }
}
