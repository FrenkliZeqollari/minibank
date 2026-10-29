package dev.frenkli.ledger.repository;

import dev.frenkli.ledger.domain.Entry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

    /** The account's balance: the sum of all its entries, in cents. */
    @Query("select coalesce(sum(e.amount), 0) from Entry e where e.accountId = :accountId")
    long sumAmountByAccountId(@Param("accountId") UUID accountId);

    List<Entry> findByTransactionId(UUID transactionId);
}
