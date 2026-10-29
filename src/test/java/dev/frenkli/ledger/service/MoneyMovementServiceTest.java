package dev.frenkli.ledger.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.repository.AccountRepository;
import dev.frenkli.ledger.repository.EntryRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Business rules are checked before anything is written. Integration tests cover the happy paths. */
class MoneyMovementServiceTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final EntryRepository entries = mock(EntryRepository.class);
    private final LedgerService ledger = mock(LedgerService.class);
    private final MoneyMovementService service = new MoneyMovementService(accounts, entries, ledger);

    private final UUID from = UUID.randomUUID();
    private final UUID to = UUID.randomUUID();

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -10_000})
    void depositRejectsNonPositiveAmounts(long amount) {
        assertRuleBroken(() -> service.deposit(to, amount, "EUR", null), BusinessRule.NON_POSITIVE_AMOUNT);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void transferRejectsNonPositiveAmounts(long amount) {
        assertRuleBroken(() -> service.transfer(from, to, amount, "EUR", null), BusinessRule.NON_POSITIVE_AMOUNT);
    }

    @Test
    void transferRejectsSameAccount() {
        assertRuleBroken(() -> service.transfer(from, from, 100, "EUR", null), BusinessRule.SAME_ACCOUNT_TRANSFER);
    }

    @Test
    void depositRejectsUnsupportedCurrency() {
        assertRuleBroken(() -> service.deposit(to, 100, "USD", null), BusinessRule.UNSUPPORTED_CURRENCY);
    }

    @Test
    void transferRejectsUnknownAccount() {
        when(accounts.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(from, to, 100, "EUR", null))
                .isInstanceOf(AccountNotFoundException.class);
        verify(ledger, never()).record(any(), any(), any());
    }

    @Test
    void transferRejectsInsufficientFunds() {
        when(accounts.findById(from)).thenReturn(Optional.of(Account.openCustomerAccount("A", "EUR")));
        when(accounts.findById(to)).thenReturn(Optional.of(Account.openCustomerAccount("B", "EUR")));
        when(entries.sumAmountByAccountId(any())).thenReturn(99L);

        assertRuleBroken(() -> service.transfer(from, to, 100, "EUR", null), BusinessRule.INSUFFICIENT_FUNDS);
    }

    private void assertRuleBroken(Runnable call, BusinessRule rule) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getRule()).isEqualTo(rule));
        verify(ledger, never()).record(any(), any(), any());
    }
}
