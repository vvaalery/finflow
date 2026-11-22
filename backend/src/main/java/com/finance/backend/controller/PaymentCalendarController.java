package com.finance.backend.controller;

import com.finance.backend.dto.request.PaymentRequest;
import com.finance.backend.dto.response.PaymentResponse;
import com.finance.backend.entity.PaymentCalendar;
import com.finance.backend.entity.User;
import com.finance.backend.repository.PaymentCalendarRepository;
import com.finance.backend.service.PaymentCalendarService;
import com.finance.internal.domain.PaymentStatus;
import com.finance.internal.security.RestGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentCalendarController {

    private final PaymentCalendarService paymentService;
    private final PaymentCalendarRepository paymentRepository;
    private final RestGuard guard;

    @GetMapping
    public List<PaymentResponse> getPayments(@RequestParam UUID companyId) {
        guard.readCompany(companyId);
        return paymentService.getPaymentsByCompany(companyId);
    }

    @GetMapping("/upcoming")
    public List<PaymentResponse> getUpcomingPayments(@RequestParam UUID companyId) {
        guard.readCompany(companyId);
        return paymentService.getUpcomingPayments(companyId);
    }

    @GetMapping("/overdue-total")
    public BigDecimal getOverdueTotal(@RequestParam UUID companyId) {
        guard.readCompany(companyId);
        return paymentService.getOverdueTotal(companyId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(@Valid @RequestBody PaymentRequest request,
                                          @RequestParam(required = false) UUID userId) {
        guard.writeCompany(request.getCompanyId());
        return paymentService.createPayment(request, guard.user().getId());
    }

    @PatchMapping("/{id}/status")
    public PaymentResponse updateStatus(@PathVariable UUID id, @RequestParam String status) {
        PaymentCalendar payment = paymentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Платёж не найден"));
        User u = guard.user();
        guard.policy().requireWriteCompany(u, payment.getCompany());

        String current = PaymentStatus.current(payment.getStatus());
        String target = PaymentStatus.normalize(status);
        boolean decision = PaymentStatus.APPROVED.equals(target) || PaymentStatus.REJECTED.equals(target);
        if (decision && !current.equals(target) && !guard.policy().isAdmin(u)) {
            throw new AccessDeniedException("Утверждать и отклонять платежи могут только руководитель или администратор");
        }
        PaymentStatus.checkPaymentTransition(current, target);
        return paymentService.updateStatus(id, target);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePayment(@PathVariable UUID id) {
        PaymentCalendar payment = paymentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Платёж не найден"));
        guard.policy().requireWriteCompany(guard.user(), payment.getCompany());
        if (PaymentStatus.PAID.equals(PaymentStatus.current(payment.getStatus()))) {
            throw new IllegalArgumentException("Оплаченный платёж удалять нельзя");
        }
        paymentService.deletePayment(id);
    }
}
