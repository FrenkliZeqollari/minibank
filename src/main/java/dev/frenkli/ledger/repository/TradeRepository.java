package dev.frenkli.ledger.repository;

import dev.frenkli.ledger.domain.Trade;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeRepository extends JpaRepository<Trade, UUID> {

    /** Shares of {@code symbol} the account holds now: bought minus sold, over its whole history. */
    @Query("""
            select coalesce(sum(case when t.side = dev.frenkli.ledger.domain.TradeSide.BUY
                                     then t.quantity else -t.quantity end), 0)
            from Trade t
            where t.accountId = :accountId and t.symbol = :symbol
            """)
    long holding(@Param("accountId") UUID accountId, @Param("symbol") String symbol);

    /** All of the account's trades in the exact order they happened. */
    List<Trade> findByAccountIdOrderBySeqAsc(UUID accountId);

    Optional<Trade> findByTransactionId(UUID transactionId);
}
