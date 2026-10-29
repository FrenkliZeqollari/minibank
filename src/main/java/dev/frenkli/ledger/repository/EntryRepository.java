package dev.frenkli.ledger.repository;

import dev.frenkli.ledger.domain.Entry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

    /** The account's balance from its full history: the sum of all its entries, in cents. */
    @Query("select coalesce(sum(e.amount), 0) from Entry e where e.accountId = :accountId")
    long sumAmountByAccountId(@Param("accountId") UUID accountId);

    List<Entry> findByTransactionId(UUID transactionId);

    /**
     * The account's statement, newest first. Entries of one transaction share a timestamp, so the
     * entry id breaks ties: without a unique sort key, a line could show up on two pages, or none.
     */
    @Query(value = """
            select new dev.frenkli.ledger.repository.StatementLine(
                e.id, t.id, t.type, t.description, e.amount, e.createdAt)
            from Entry e, LedgerTransaction t
            where t.id = e.transactionId and e.accountId = :accountId
            order by e.createdAt desc, e.id desc
            """,
            countQuery = "select count(e) from Entry e where e.accountId = :accountId")
    Page<StatementLine> findStatement(@Param("accountId") UUID accountId, Pageable pageable);
}
