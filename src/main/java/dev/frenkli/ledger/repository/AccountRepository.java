package dev.frenkli.ledger.repository;

import dev.frenkli.ledger.domain.Account;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Adds a signed amount to the cached balance. Done as one SQL UPDATE ({@code balance = balance
     * + :amount}) rather than read-modify-write in Java, so the database adds to the latest value
     * and two concurrent bookings cannot overwrite each other's update.
     *
     * @return the number of updated rows (1, or 0 if the account does not exist)
     */
    @Modifying(flushAutomatically = true)
    @Query("update Account a set a.balance = a.balance + :amount where a.id = :id")
    int addToBalance(@Param("id") UUID id, @Param("amount") long amount);
}
