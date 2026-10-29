package dev.frenkli.ledger.repository;

import dev.frenkli.ledger.domain.Instrument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentRepository extends JpaRepository<Instrument, String> {

    List<Instrument> findAllByOrderBySymbolAsc();
}
