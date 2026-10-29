package dev.frenkli.ledger.repository;

import dev.frenkli.ledger.domain.InstrumentPrice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentPriceRepository extends JpaRepository<InstrumentPrice, UUID> {

    /** The current price: the most recently recorded one. */
    Optional<InstrumentPrice> findFirstBySymbolOrderBySeqDesc(String symbol);

    /** The most recent prices, newest first; the Pageable limits how many. */
    List<InstrumentPrice> findBySymbolOrderBySeqDesc(String symbol, Pageable pageable);
}
