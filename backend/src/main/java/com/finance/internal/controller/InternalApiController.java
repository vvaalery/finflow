package com.finance.internal.controller;

import com.finance.internal.dto.GraphQLInput.*;
import com.finance.internal.dto.GraphQLView.*;
import com.finance.internal.service.InternalCompanyService;
import com.finance.internal.service.InternalForecastService;
import com.finance.internal.service.InternalPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class InternalApiController {
    private final InternalCompanyService companies;
    private final InternalPaymentService payments;
    private final InternalForecastService forecasts;

    @GetMapping("/companies")
    public List<CompanyView> companies() {
        return companies.companies();
    }

    @GetMapping("/companies/{id}")
    public CompanyView company(@PathVariable UUID id) {
        return companies.company(id);
    }

    @PostMapping("/companies")
    public MutationResult createCompany(
            @RequestParam(required = false) UUID ownerId,
            @Valid @RequestBody CompanyInput body) {
        return companies.createCompany(body, ownerId);
    }

    @PutMapping("/companies/{id}")
    public MutationResult updateCompany(
            @PathVariable UUID id,
            @Valid @RequestBody CompanyInput body) {
        return companies.updateCompany(id, body);
    }

    @DeleteMapping("/companies/{id}")
    public MutationResult deleteCompany(@PathVariable UUID id) {
        return companies.deleteCompany(id);
    }

    @GetMapping("/accounts")
    public List<AccountView> accounts(@RequestParam UUID companyId) {
        return companies.accounts(companyId);
    }

    @GetMapping("/accounts/{id}")
    public AccountView account(@PathVariable UUID id) {
        return companies.account(id);
    }

    @PostMapping("/accounts")
    public MutationResult createAccount(@Valid @RequestBody AccountInput body) {
        return companies.createAccount(body);
    }

    @PutMapping("/accounts/{id}")
    public MutationResult updateAccount(
            @PathVariable UUID id,
            @Valid @RequestBody AccountInput body) {
        return companies.updateAccount(id, body);
    }

    @DeleteMapping("/accounts/{id}")
    public MutationResult deleteAccount(@PathVariable UUID id) {
        return companies.deleteAccount(id);
    }

    @PostMapping("/transactions/search")
    public List<TransactionView> searchTransactions(@RequestBody TransactionQuery query) {
        return companies.transactions(query);
    }

    @GetMapping("/transactions/aggregate")
    public AggregateView aggregate(
            @RequestParam UUID companyId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return companies.aggregate(companyId, from, to);
    }

    @GetMapping("/transactions/{id}")
    public TransactionView transaction(@PathVariable UUID id) {
        return companies.transaction(id);
    }

    @PostMapping("/transactions")
    public MutationResult createTransaction(@Valid @RequestBody TransactionInput body) {
        return companies.createTransaction(body);
    }

    @PutMapping("/transactions/{id}")
    public MutationResult updateTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody TransactionInput body) {
        return companies.updateTransaction(id, body);
    }

    @DeleteMapping("/transactions/{id}")
    public MutationResult deleteTransaction(@PathVariable UUID id) {
        return companies.deleteTransaction(id);
    }

    @GetMapping("/payments")
    public List<PaymentView> payments(@RequestParam UUID companyId) {
        return payments.payments(companyId);
    }

    @PostMapping("/payments")
    public MutationResult createPayment(@Valid @RequestBody PaymentInput body) {
        return payments.createPayment(body);
    }

    @PutMapping("/payments/{id}")
    public MutationResult updatePayment(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentInput body) {
        return payments.updatePayment(id, body);
    }

    @DeleteMapping("/payments/{id}")
    public MutationResult deletePayment(@PathVariable UUID id) {
        return payments.deletePayment(id);
    }

    @GetMapping("/payment-requests")
    public List<PaymentRequestView> paymentRequests() {
        return payments.paymentRequests();
    }

    @GetMapping("/payment-requests/{id}")
    public PaymentRequestView paymentRequest(@PathVariable UUID id) {
        return payments.paymentRequest(id);
    }

    @PostMapping("/payment-requests")
    public MutationResult createPaymentRequest(@Valid @RequestBody PaymentRequestInput body) {
        return payments.createPaymentRequest(body);
    }

    @PostMapping("/payment-requests/{id}/approve")
    public MutationResult approvePaymentRequest(@PathVariable UUID id) {
        return payments.approvePaymentRequest(id);
    }

    @PostMapping("/payment-requests/{id}/reject")
    public MutationResult rejectPaymentRequest(@PathVariable UUID id) {
        return payments.rejectPaymentRequest(id);
    }

    @GetMapping("/forecasts")
    public List<ForecastView> forecasts(@RequestParam UUID companyId) {
        return forecasts.forecasts(companyId);
    }

    @PostMapping("/forecasts/generate")
    public ForecastResultView generateForecast(@Valid @RequestBody GenerateForecastInput body) {
        return forecasts.generate(body);
    }
}
