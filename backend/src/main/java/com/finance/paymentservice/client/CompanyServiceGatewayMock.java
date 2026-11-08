package com.finance.paymentservice.client;

import com.finance.backend.entity.Account;
import com.finance.backend.repository.AccountRepository;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class CompanyServiceGatewayMock implements CompanyServiceGateway {
    private final AccountRepository accounts;
    public CompanyServiceGatewayMock(AccountRepository accounts){this.accounts=accounts;}
    public Account getAccount(UUID accountId){return accounts.findById(accountId).orElse(null);}
    public boolean accountBelongsToCompany(UUID accountId, UUID companyId){Account a=getAccount(accountId);return a!=null && a.getCompany()!=null && a.getCompany().getId().equals(companyId);}
}
