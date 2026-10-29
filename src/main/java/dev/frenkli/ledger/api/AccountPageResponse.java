package dev.frenkli.ledger.api;

import dev.frenkli.ledger.domain.Account;
import java.util.List;
import org.springframework.data.domain.Page;

public record AccountPageResponse(List<AccountResponse> accounts, int page, int size, long totalAccounts, int totalPages) {

    static AccountPageResponse from(Page<Account> page) {
        return new AccountPageResponse(page.getContent().stream().map(AccountResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
