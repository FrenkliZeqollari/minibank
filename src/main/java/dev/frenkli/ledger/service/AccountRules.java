package dev.frenkli.ledger.service;

import dev.frenkli.ledger.domain.Account;
import dev.frenkli.ledger.domain.AccountStatus;
import dev.frenkli.ledger.domain.AccountType;

/** Checks on accounts shared by every operation that moves money. */
final class AccountRules {

    private AccountRules() {
    }

    static Account requireUsableCustomerAccount(Account account) {
        // System accounts are moved only by the bank's own bookings, never through this API.
        if (account.getType() != AccountType.CUSTOMER || account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException(BusinessRule.ACCOUNT_NOT_USABLE,
                    "Account " + account.getId() + " is not an active customer account");
        }
        return account;
    }

    static void requireSameCurrency(Account account, String currency) {
        if (!account.getCurrency().equals(currency)) {
            throw new BusinessRuleException(BusinessRule.CURRENCY_MISMATCH,
                    "Account " + account.getId() + " is in " + account.getCurrency() + ", request is in " + currency);
        }
    }
}
