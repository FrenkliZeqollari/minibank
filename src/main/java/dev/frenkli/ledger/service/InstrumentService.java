package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Instrument;
import dev.frenkli.ledger.domain.InstrumentPrice;
import dev.frenkli.ledger.repository.InstrumentPriceRepository;
import dev.frenkli.ledger.repository.InstrumentRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Instruments and their market prices. Prices are entered by hand; there is no live price feed. */
@Service
public class InstrumentService {

    private final InstrumentRepository instruments;
    private final InstrumentPriceRepository prices;

    public InstrumentService(InstrumentRepository instruments, InstrumentPriceRepository prices) {
        this.instruments = instruments;
        this.prices = prices;
    }

    /** An instrument and its current price, if one was ever recorded. */
    public record InstrumentWithPrice(Instrument instrument, Optional<InstrumentPrice> latestPrice) {
    }

    @Transactional
    public Instrument create(String symbol, String name, String currency) {
        Currencies.requireSupported(currency);
        if (instruments.existsById(symbol)) {
            throw new InstrumentAlreadyExistsException(symbol);
        }
        return instruments.save(new Instrument(symbol, name, currency));
    }

    @Transactional(readOnly = true)
    public List<InstrumentWithPrice> list() {
        return instruments.findAllByOrderBySymbolAsc().stream()
                .map(i -> new InstrumentWithPrice(i, prices.findFirstBySymbolOrderBySeqDesc(i.getSymbol())))
                .toList();
    }

    /** Records a new market price, in minor units (cents) per share. Earlier prices are kept. */
    @Transactional
    public InstrumentPrice recordPrice(String symbol, long price) {
        if (price <= 0) {
            throw new BusinessRuleException(BusinessRule.NON_POSITIVE_AMOUNT,
                    "Price must be greater than 0 cents, got " + price);
        }
        if (!instruments.existsById(symbol)) {
            throw new InstrumentNotFoundException(symbol);
        }
        return prices.save(new InstrumentPrice(symbol, price));
    }
}
