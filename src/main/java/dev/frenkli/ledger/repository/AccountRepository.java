package dev.frenkli.ledger.repository;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.domain.AccountType;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Page<Account> findByTypeOrderByCreatedAtAscIdAsc(AccountType type, Pageable pageable);

    /**
     * Loads the account and locks its row until the DB transaction ends ({@code SELECT ... FOR
     * UPDATE}; on Postgres Hibernate uses the {@code FOR NO KEY UPDATE} variant, which blocks
     * other writers of the row but not the inserts of entries that reference it). Another
     * transaction locking the same account waits here until this one commits, and then reads the
     * balance as it is after that commit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Adds a signed amount to the cached balance. Done as one SQL UPDATE ({@code balance = balance
     * + :amount}) rather than read-modify-write in Java, so the database adds to the latest value
     * and two concurrent bookings cannot overwrite each other's update.
     *
     * <p>The UPDATE bypasses JPA's cache of loaded objects (the persistence context). Without
     * {@code clearAutomatically}, an Account loaded earlier in the same DB transaction would keep
     * showing the old balance, and a second booking in that transaction would check against it.
     * Pending changes are written first ({@code flushAutomatically}), then the cache is cleared,
     * so the next read loads the account fresh from the database.
     *
     * @return the number of updated rows (1, or 0 if the account does not exist)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Account a set a.balance = a.balance + :amount where a.id = :id")
    int addToBalance(@Param("id") UUID id, @Param("amount") long amount);
}
