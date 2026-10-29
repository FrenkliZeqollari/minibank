package dev.frenkli.ledger.api;

import dev.frenkli.ledger.service.LedgerHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Ledger", description = "Whole-ledger consistency checks")
public class LedgerHealthController {

    private final LedgerHealthService ledgerHealthService;

    public LedgerHealthController(LedgerHealthService ledgerHealthService) {
        this.ledgerHealthService = ledgerHealthService;
    }

    /**
     * 200 when the books balance, 503 when they do not. The status code is what monitoring,
     * load balancers and uptime checks look at, so a broken ledger raises an alarm without anyone
     * parsing the body. The body is the same in both cases and says what is wrong.
     */
    @GetMapping("/ledger/health")
    @Operation(summary = "Check that the books balance",
            description = "healthy is true when all entries sum to 0 and every account's cached balance equals the "
                    + "sum of its entries.")
    @ApiResponse(responseCode = "200", description = "The books balance.")
    @ApiResponse(responseCode = "503", description = "The books do NOT balance; the body lists what is wrong.",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = LedgerHealthResponse.class)))
    public ResponseEntity<LedgerHealthResponse> health() {
        LedgerHealthResponse health = LedgerHealthResponse.from(ledgerHealthService.check());
        return ResponseEntity.status(health.healthy() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(health);
    }
}
