package dev.frenkli.ledger.api;

import dev.frenkli.ledger.service.LedgerHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Ledger", description = "Whole-ledger consistency checks")
public class LedgerHealthController {

    private final LedgerHealthService ledgerHealthService;

    public LedgerHealthController(LedgerHealthService ledgerHealthService) {
        this.ledgerHealthService = ledgerHealthService;
    }

    /** Always 200; {@code healthy: false} means the books do not balance and someone must look. */
    @GetMapping("/ledger/health")
    @Operation(summary = "Check that the books balance",
            description = "Always 200. healthy is true when all entries sum to 0 and every account's cached "
                    + "balance equals the sum of its entries.")
    public LedgerHealthResponse health() {
        return LedgerHealthResponse.from(ledgerHealthService.check());
    }
}
