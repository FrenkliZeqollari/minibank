package dev.frenkli.ledger.api;

import dev.frenkli.ledger.service.MoneyMovementService;
import dev.frenkli.ledger.service.MovementResult;
import dev.frenkli.ledger.service.ReversalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
@Tag(name = "Money movements", description = "Deposits, transfers and reversals. All require an Idempotency-Key.")
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
    @Operation(summary = "Deposit money into a customer account",
            description = "Money enters the bank: booked as EXTERNAL_FUNDING −amount / customer +amount.")
    @ApiDocs.MoneyMovementResponses
    @ApiResponse(responseCode = "422",
            description = "Business rule broken: amount ≤ 0, unsupported currency, currency mismatch, target is not "
                    + "an active customer account, or Idempotency-Key reused for a different request.",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<TransactionResponse> deposit(
            @Parameter(description = ApiDocs.IDEMPOTENCY_KEY_DESCRIPTION, example = ApiDocs.IDEMPOTENCY_KEY_EXAMPLE)
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 255) String idempotencyKey,
            @Valid @RequestBody DepositRequest request) {
        return created(moneyMovementService.deposit(idempotencyKey,
                request.accountId(), request.amount(), request.currency(), request.description()));
    }

    @PostMapping("/transfers")
    @Operation(summary = "Transfer money between two customer accounts",
            description = "Booked as from −amount / to +amount. Never overdraws the sending account, even under "
                    + "concurrent requests.")
    @ApiDocs.MoneyMovementResponses
    @ApiResponse(responseCode = "422",
            description = "Business rule broken: insufficient funds, amount ≤ 0, transfer to the same account, "
                    + "unsupported currency, currency mismatch, an account that is not an active customer account, "
                    + "or Idempotency-Key reused for a different request.",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<TransactionResponse> transfer(
            @Parameter(description = ApiDocs.IDEMPOTENCY_KEY_DESCRIPTION, example = ApiDocs.IDEMPOTENCY_KEY_EXAMPLE)
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 255) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return created(moneyMovementService.transfer(idempotencyKey,
                request.fromAccountId(), request.toAccountId(), request.amount(),
                request.currency(), request.description()));
    }

    /** Cancels a transaction by booking its mirror image. A transaction can be reversed once (409). */
    @PostMapping("/transactions/{id}/reversal")
    @Operation(summary = "Reverse a transaction",
            description = "Books a new REVERSAL transaction whose entries mirror the original with opposite signs. "
                    + "The original stays in the history. A transaction can be reversed once.")
    @ApiDocs.MoneyMovementResponses
    @ApiResponse(responseCode = "409", description = "The transaction has already been reversed.",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422",
            description = "The transaction is itself a reversal, a customer no longer has the money to give back, "
                    + "or Idempotency-Key reused for a different request.",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<TransactionResponse> reverse(
            @Parameter(description = ApiDocs.IDEMPOTENCY_KEY_DESCRIPTION, example = ApiDocs.IDEMPOTENCY_KEY_EXAMPLE)
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 255) String idempotencyKey,
            @Parameter(description = "Id of the transaction to reverse") @PathVariable UUID id) {
        return created(reversalService.reverse(idempotencyKey, id));
    }

    /** A replay gets the same status and body as the original response. */
    private static ResponseEntity<TransactionResponse> created(MovementResult result) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(IDEMPOTENT_REPLAYED, String.valueOf(result.replayed()))
                .body(TransactionResponse.from(result.recorded()));
    }
}
