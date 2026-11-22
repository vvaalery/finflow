package com.finance.backend.controller;

import com.finance.backend.entity.PaymentCalendar;
import com.finance.backend.entity.PaymentRequest;
import com.finance.backend.entity.User;
import com.finance.backend.repository.PaymentCalendarRepository;
import com.finance.backend.repository.PaymentRequestRepository;
import com.finance.internal.domain.PaymentStatus;
import com.finance.internal.security.AccessPolicy;
import com.finance.internal.security.RestGuard;
import com.finance.internal.service.InternalPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER','DIRECTOR')")
@RestController
@RequestMapping("/api/payment-requests")
@RequiredArgsConstructor
public class PaymentRequestController {

    private final PaymentRequestRepository repository;
    private final PaymentCalendarRepository calendarRepository;
    private final InternalPaymentService internalPayments;
    private final RestGuard guard;

    @GetMapping
    public List<PaymentRequest> getAll() {
        User u = guard.user();
        AccessPolicy policy = guard.policy();

        List<PaymentRequest> requests = repository.findAll().stream()
                .filter(r -> policy.canReadAll(u)
                        || (policy.isManager(u) && r.getAccount() != null && policy.owns(u, r.getAccount().getCompany())))
                .collect(Collectors.toList());

        Set<String> existingKeys = requests.stream()
                .map(r -> r.getTitle() + "_" + r.getAmount() + "_" + r.getDate())
                .collect(Collectors.toSet());

        List<PaymentCalendar> calendarItems = calendarRepository.findAll().stream()
                .filter(c -> policy.canReadAll(u) || (policy.isManager(u) && policy.owns(u, c.getCompany())))
                .collect(Collectors.toList());

        List<PaymentRequest> virtualRequests = calendarItems.stream()
                .filter(item -> !existingKeys.contains(item.getTitle() + "_" + item.getAmount() + "_" + item.getDueDate()))
                .map(item -> {
                    PaymentRequest req = new PaymentRequest();
                    req.setId(item.getId());
                    req.setTitle(item.getTitle());
                    req.setAmount(item.getAmount());
                    req.setDate(item.getDueDate());
                    req.setCategory(item.getCategory());
                    req.setCounterparty(item.getCounterparty());
                    req.setStatus(item.getStatus() != null ? item.getStatus() : "PENDING");
                    req.setRequester("Система (Календарь)");
                    return req;
                }).collect(Collectors.toList());

        List<PaymentRequest> combined = new ArrayList<>();
        combined.addAll(requests);
        combined.addAll(virtualRequests);

        LocalDate today = LocalDate.now();
        return combined.stream()
                .sorted((a, b) -> {
                    if (a.getDate() == null || b.getDate() == null) return 0;
                    boolean aOverdue = "PENDING".equals(a.getStatus()) && a.getDate().isBefore(today);
                    boolean bOverdue = "PENDING".equals(b.getStatus()) && b.getDate().isBefore(today);
                    if (aOverdue && !bOverdue) return -1;
                    if (!aOverdue && bOverdue) return 1;
                    return b.getDate().compareTo(a.getDate());
                })
                .collect(Collectors.toList());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR')")
    @Transactional
    public PaymentRequest updateStatus(@PathVariable UUID id, @RequestParam String status) {
        String target = PaymentStatus.normalize(status);
        User u = guard.user();
        AccessPolicy policy = guard.policy();

        PaymentCalendar calItem = calendarRepository.findById(id).orElse(null);
        if (calItem != null) {
            policy.requireReadCompany(u, calItem.getCompany());
            PaymentStatus.checkPaymentTransition(PaymentStatus.current(calItem.getStatus()), target);
            calItem.setStatus(target);
            calendarRepository.save(calItem);

            PaymentRequest matching = repository.findAll().stream()
                    .filter(r -> Objects.equals(r.getTitle(), calItem.getTitle())
                            && r.getAmount() != null && calItem.getAmount() != null
                            && r.getAmount().compareTo(calItem.getAmount()) == 0
                            && Objects.equals(r.getDate(), calItem.getDueDate()))
                    .findFirst().orElse(null);
            if (matching != null) {
                matching.setStatus(target);
                return repository.save(matching);
            }
            PaymentRequest view = new PaymentRequest();
            view.setId(calItem.getId());
            view.setTitle(calItem.getTitle());
            view.setAmount(calItem.getAmount());
            view.setDate(calItem.getDueDate());
            view.setCategory(calItem.getCategory());
            view.setCounterparty(calItem.getCounterparty());
            view.setRequester("Система (Календарь)");
            view.setStatus(target);
            return view;
        }

        PaymentRequest request = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Заявка с ID " + id + " не найдена"));
        if (request.getAccount() != null) policy.requireReadCompany(u, request.getAccount().getCompany());

        if (PaymentStatus.APPROVED.equals(target)) {
            internalPayments.approvePaymentRequest(id);
        } else if (PaymentStatus.REJECTED.equals(target)) {
            internalPayments.rejectPaymentRequest(id);
        } else if (PaymentStatus.PAID.equals(target)) {
            PaymentStatus.checkRequestTransition(PaymentStatus.current(request.getStatus()), PaymentStatus.PAID);
            request.setStatus(PaymentStatus.PAID);
            repository.save(request);
            if (request.getAccount() != null) {
                calendarRepository.findByCompanyId(request.getAccount().getCompany().getId()).stream()
                        .filter(c -> Objects.equals(c.getTitle(), request.getTitle())
                                && c.getAmount() != null && request.getAmount() != null
                                && c.getAmount().compareTo(request.getAmount()) == 0
                                && Objects.equals(c.getDueDate(), request.getDate()))
                        .findFirst()
                        .ifPresent(c -> {
                            String cur = PaymentStatus.current(c.getStatus());
                            if (!PaymentStatus.PAID.equals(cur)) {
                                PaymentStatus.checkPaymentTransition(cur, PaymentStatus.PAID);
                                c.setStatus(PaymentStatus.PAID);
                                calendarRepository.save(c);
                            }
                        });
            }
        } else {
            throw new IllegalArgumentException("Недопустимый статус заявки: " + target);
        }
        return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Заявка не найдена"));
    }
}
