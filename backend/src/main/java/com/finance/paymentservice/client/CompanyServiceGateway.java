package com.finance.paymentservice.client;

import com.finance.backend.entity.Account;
import java.util.UUID;

public interface CompanyServiceGateway {
    Account getAccount(UUID accountId);
    boolean accountBelongsToCompany(UUID accountId, UUID companyId);
}
