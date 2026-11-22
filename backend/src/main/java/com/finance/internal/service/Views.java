package com.finance.internal.service;

import com.finance.backend.entity.*;
import com.finance.internal.dto.GraphQLView.*;

import java.util.UUID;

final class Views {
    private Views() {}

    static MutationResult result(UUID id, String message) { return new MutationResult(true, message, id.toString()); }

    static CompanyView company(Company c) {
        return new CompanyView(c.getId(), c.getName(), c.getTaxId(), c.getAddress(), c.getPhone(), c.getDescription(),
                c.getWebsiteUrl(), c.getLogoUrl(), c.getStatus(), c.getOwner().getId(), c.getCreatedAt());
    }

    static AccountView account(Account a) {
        return new AccountView(a.getId(), a.getCompany().getId(), a.getAccountName(), a.getAccountType(), a.getCurrency(),
                a.getBalance(), Boolean.TRUE.equals(a.getIsActive()), a.getCreatedAt());
    }

    static TransactionView transaction(Transaction t) {
        return new TransactionView(t.getId(), t.getAccount().getId(), t.getTransactionType(), t.getAmount(), t.getDate(),
                t.getDescription(), t.getCategory(), t.getCounterparty(), t.getCfoId(), t.getCreatedAt());
    }

    static PaymentView payment(PaymentCalendar p) {
        return new PaymentView(p.getId(), p.getCompany().getId(), p.getTitle(), p.getDescription(), p.getAmount(), p.getDueDate(),
                p.getStatus(), p.getCategory(), p.getCounterparty(), p.getCreatedBy() == null ? null : p.getCreatedBy().getId(), p.getCreatedAt());
    }

    static PaymentRequestView request(PaymentRequest r) {
        Account a = r.getAccount();
        return new PaymentRequestView(r.getId(), a == null ? null : a.getId(), a == null ? null : a.getCompany().getId(),
                r.getTitle(), r.getAmount(), r.getDate(), r.getStatus(), r.getRequester(), r.getCounterparty(), r.getCategory(), r.getDescription());
    }

    static ForecastView forecast(CashFlowForecast f) {
        return new ForecastView(f.getId(), f.getCompany().getId(), f.getForecastPeriodStart(), f.getForecastPeriodEnd(),
                f.getTotalProjectedInflow(), f.getTotalProjectedOutflow(), f.getNetCashFlow(), f.getStrategyUsed(), f.getCreatedAt());
    }
}
