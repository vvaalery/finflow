package com.finance.paymentservice.client;

import java.util.UUID;

public interface CompanyServiceGateway {
    boolean accountBelongsToCompany(UUID accountId, UUID companyId);
}
