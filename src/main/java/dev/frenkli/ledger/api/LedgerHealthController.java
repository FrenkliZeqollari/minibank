package dev.frenkli.ledger.api;

import dev.frenkli.ledger.service.LedgerHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LedgerHealthController {

    private final LedgerHealthService ledgerHealthService;

    public LedgerHealthController(LedgerHealthService ledgerHealthService) {
        this.ledgerHealthService = ledgerHealthService;
    }

    /** Always 200; {@code healthy: false} means the books do not balance and someone must look. */
    @GetMapping("/ledger/health")
    public LedgerHealthResponse health() {
        return LedgerHealthResponse.from(ledgerHealthService.check());
    }
}
