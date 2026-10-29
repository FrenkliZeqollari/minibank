package dev.frenkli.ledger.api;

import dev.frenkli.ledger.domain.Instrument;
import dev.frenkli.ledger.domain.InstrumentPrice;
import dev.frenkli.ledger.service.InstrumentService.InstrumentWithPrice;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record InstrumentResponse(
        String symbol,
        String name,
        String currency,
        @Schema(description = "Current price per share in cents; null if no price was recorded yet", example = "1234")
        Long lastPrice,
        Instant priceAsOf) {

    static InstrumentResponse from(Instrument instrument) {
        return new InstrumentResponse(instrument.getSymbol(), instrument.getName(), instrument.getCurrency(), null, null);
    }

    static InstrumentResponse from(InstrumentWithPrice withPrice) {
        Instrument i = withPrice.instrument();
        InstrumentPrice p = withPrice.latestPrice().orElse(null);
        return new InstrumentResponse(i.getSymbol(), i.getName(), i.getCurrency(),
                p == null ? null : p.getPrice(), p == null ? null : p.getCreatedAt());
    }
}
