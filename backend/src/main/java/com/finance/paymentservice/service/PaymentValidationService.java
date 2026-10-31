package com.finance.paymentservice.service;

import com.finance.paymentservice.client.CompanyServiceGateway;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class PaymentValidationService {
    private final CompanyServiceGateway companies;
    public PaymentValidationService(CompanyServiceGateway companies){this.companies=companies;}
    public void validateAccountOwnership(UUID accountId, UUID companyId){
        if(accountId==null) throw new IllegalArgumentException("Для платёжной заявки необходимо указать accountId");
        if(!companies.accountBelongsToCompany(accountId,companyId)) throw new IllegalArgumentException("Счёт не существует или не принадлежит указанной компании");
    }
}
