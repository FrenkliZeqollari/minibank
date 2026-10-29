package dev.frenkli.ledger.api;

import dev.frenkli.ledger.service.AccountInsightService.DailyBalance;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BalanceHistoryResponse(UUID accountId, List<Point> points) {

    @Schema(name = "DailyBalance")
    public record Point(
            @Schema(description = "Business day (bank's time zone)", example = "2026-10-29") LocalDate date,
            @Schema(description = "Balance at the end of that day, in cents", example = "7500") long balance) {
    }

    static BalanceHistoryResponse from(UUID accountId, List<DailyBalance> history) {
        return new BalanceHistoryResponse(accountId,
                history.stream().map(d -> new Point(d.date(), d.balance())).toList());
    }
}
