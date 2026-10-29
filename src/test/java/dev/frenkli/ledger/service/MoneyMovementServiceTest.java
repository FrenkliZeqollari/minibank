package dev.frenkli.ledger.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.repository.AccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Business rules are checked before anything is written. Integration tests cover the happy paths. */
class MoneyMovementServiceTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final LedgerService ledger = mock(LedgerService.class);
    private final IdempotencyService idempotency = mock(IdempotencyService.class); // returns Optional.empty()
    private final MoneyMovementService service = new MoneyMovementService(new AccountLocks(accounts), ledger, idempotency);

    private final UUID from = UUID.randomUUID();
    private final UUID to = UUID.randomUUID();

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -10_000})
    void depositRejectsNonPositiveAmounts(long amount) {
        assertRuleBroken(() -> service.deposit("key", to, amount, "EUR", null), BusinessRule.NON_POSITIVE_AMOUNT);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void transferRejectsNonPositiveAmounts(long amount) {
        assertRuleBroken(() -> service.transfer("key", from, to, amount, "EUR", null), BusinessRule.NON_POSITIVE_AMOUNT);
    }

    @Test
    void transferRejectsSameAccount() {
        assertRuleBroken(() -> service.transfer("key", from, from, 100, "EUR", null), BusinessRule.SAME_ACCOUNT_TRANSFER);
    }

    @Test
    void depositRejectsUnsupportedCurrency() {
        assertRuleBroken(() -> service.deposit("key", to, 100, "USD", null), BusinessRule.UNSUPPORTED_CURRENCY);
    }

    @Test
    void transferRejectsUnknownAccount() {
        when(accounts.findByIdForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer("key", from, to, 100, "EUR", null))
                .isInstanceOf(AccountNotFoundException.class);
        verify(ledger, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void transferRejectsInsufficientFunds() {
        // A newly opened account has a balance of 0.
        when(accounts.findByIdForUpdate(from)).thenReturn(Optional.of(Account.openCustomerAccount("A", "EUR")));
        when(accounts.findByIdForUpdate(to)).thenReturn(Optional.of(Account.openCustomerAccount("B", "EUR")));

        assertRuleBroken(() -> service.transfer("key", from, to, 100, "EUR", null), BusinessRule.INSUFFICIENT_FUNDS);
    }

    private void assertRuleBroken(Runnable call, BusinessRule rule) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getRule()).isEqualTo(rule));
        verify(ledger, never()).record(any(), any(), any(), any(), any());
    }
}
