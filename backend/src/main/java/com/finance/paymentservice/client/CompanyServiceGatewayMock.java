package com.finance.paymentservice.client;

import com.finance.backend.entity.Account;
import com.finance.backend.repository.AccountRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Profile("mock")
public class CompanyServiceGatewayMock implements CompanyServiceGateway {

    private final AccountRepository accounts;

    public CompanyServiceGatewayMock(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean accountBelongsToCompany(UUID accountId, UUID companyId) {
        Account account = accounts.findById(accountId).orElse(null);

        return account != null
                && account.getCompany() != null
                && account.getCompany().getId().equals(companyId);
    }
}
