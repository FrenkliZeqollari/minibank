package dev.frenkli.ledger.api;

import dev.frenkli.ledger.service.AccountInsightService.MonthlyTotals;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record MonthlySummaryResponse(UUID accountId, List<Month> months) {

    @Schema(name = "MonthlyTotals")
    public record Month(
            @Schema(description = "Calendar month in the bank's time zone", example = "2026-10") String month,
            @Schema(description = "Money that came in, in cents", example = "250000") long moneyIn,
            @Schema(description = "Money that went out, in cents (a positive number)", example = "180000") long moneyOut) {
    }

    static MonthlySummaryResponse from(UUID accountId, List<MonthlyTotals> totals) {
        return new MonthlySummaryResponse(accountId,
                totals.stream().map(t -> new Month(t.month().toString(), t.moneyIn(), t.moneyOut())).toList());
    }
}
