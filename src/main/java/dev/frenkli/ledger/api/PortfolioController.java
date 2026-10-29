package dev.frenkli.ledger.api;

import dev.frenkli.ledger.service.PortfolioService;
import dev.frenkli.ledger.service.TradeResult;
import dev.frenkli.ledger.service.TradeService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts/{id}")
@Tag(name = "Portfolio", description = "Buying and selling shares with an account's cash, and the resulting portfolio")
public class PortfolioController {

    private final TradeService tradeService;
    private final PortfolioService portfolioService;

    public PortfolioController(TradeService tradeService, PortfolioService portfolioService) {
        this.tradeService = tradeService;
        this.portfolioService = portfolioService;
    }

    @PostMapping("/trades")
    @Operation(summary = "Buy or sell shares",
            description = "Executes at the instrument's current price. The cash side is booked in the ledger as a "
                    + "TRADE transaction against the SECURITIES_SETTLEMENT system account.")
    @ApiDocs.MoneyMovementResponses
    @ApiResponse(responseCode = "422",
            description = "Business rule broken: insufficient funds (buy), insufficient holdings (sell), quantity < 1, "
                    + "no market price yet, currency mismatch, not an active customer account, or Idempotency-Key "
                    + "reused for a different request.",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<TradeResponse> trade(
            @Parameter(description = ApiDocs.IDEMPOTENCY_KEY_DESCRIPTION, example = ApiDocs.IDEMPOTENCY_KEY_EXAMPLE)
            @RequestHeader(MoneyMovementController.IDEMPOTENCY_KEY) @NotBlank @Size(max = 255) String idempotencyKey,
            @PathVariable UUID id,
            @Valid @RequestBody TradeRequest request) {
        TradeResult result = tradeService.trade(idempotencyKey, id, request.side(), request.symbol(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(MoneyMovementController.IDEMPOTENT_REPLAYED, String.valueOf(result.replayed()))
                .body(TradeResponse.from(result.trade()));
    }

    @GetMapping("/portfolio")
    @Operation(summary = "Get the account's portfolio",
            description = "Positions valued at the latest market prices, with cost basis (average cost method) and "
                    + "realized and unrealized profit or loss.")
    @ApiResponse(responseCode = "200", description = "The portfolio.")
    @ApiResponse(responseCode = "404", description = "Unknown account.",
            content = @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    public PortfolioResponse portfolio(@PathVariable UUID id) {
        return PortfolioResponse.from(portfolioService.portfolio(id));
    }
}
