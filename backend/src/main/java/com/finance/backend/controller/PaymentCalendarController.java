package com.finance.backend.controller;

import com.finance.backend.dto.request.PaymentRequest;
import com.finance.backend.dto.response.PaymentResponse;
import com.finance.backend.service.PaymentCalendarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentCalendarController {
    
    private final PaymentCalendarService paymentService;
    
    @GetMapping
    public List<PaymentResponse> getPayments(@RequestParam UUID companyId) {
        return paymentService.getPaymentsByCompany(companyId);
    }
    
    @GetMapping("/upcoming")
    public List<PaymentResponse> getUpcomingPayments(@RequestParam UUID companyId) {
        return paymentService.getUpcomingPayments(companyId);
    }
    
    @GetMapping("/overdue-total")
    public BigDecimal getOverdueTotal(@RequestParam UUID companyId) {
        return paymentService.getOverdueTotal(companyId);
    }
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(@Valid @RequestBody PaymentRequest request,
                                          @RequestParam UUID userId) {
        return paymentService.createPayment(request, userId);
    }
    
    @PatchMapping("/{id}/status")
    public PaymentResponse updateStatus(@PathVariable UUID id, @RequestParam String status) {
        return paymentService.updateStatus(id, status);
    }
    
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayment(@PathVariable UUID id) {
        paymentService.deletePayment(id);
    }
}