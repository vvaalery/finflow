
package com.finance.backend.service;

import com.finance.backend.dto.request.PaymentRequest;
import com.finance.backend.dto.response.PaymentResponse;
import com.finance.backend.entity.Company;
import com.finance.backend.entity.PaymentCalendar;
import com.finance.backend.entity.ReferenceCashFlowItem;
import com.finance.backend.entity.ReferenceCounterparty;
import com.finance.backend.entity.User;
import com.finance.backend.repository.CompanyRepository;
import com.finance.backend.repository.PaymentCalendarRepository;
import com.finance.backend.repository.ReferenceCashFlowItemRepository;
import com.finance.backend.repository.ReferenceCounterpartyRepository;
import com.finance.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentCalendarService {

    private final PaymentCalendarRepository paymentRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final ReferenceCashFlowItemRepository cashFlowItemRepository;
    private final ReferenceCounterpartyRepository counterpartyRepository;

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request, UUID userId) {
        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Компания не найдена"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        PaymentCalendar payment = new PaymentCalendar();
        payment.setCompany(company);
        payment.setTitle(request.getTitle());
        payment.setDescription(request.getDescription());
        payment.setAmount(request.getAmount());
        payment.setDueDate(request.getDueDate());
        payment.setStatus("PENDING");
        payment.setCreatedBy(user);

        ReferenceCashFlowItem cashFlowItem = null;

        if (request.getCashFlowItemId() != null) {
            cashFlowItem = cashFlowItemRepository.findById(request.getCashFlowItemId())
                    .orElseThrow(() -> new RuntimeException("Статья ДДС не найдена"));
        } else if (request.getCategory() != null && !request.getCategory().isBlank()) {
            cashFlowItem = cashFlowItemRepository.findAll().stream()
                    .filter(item -> item.getName().equalsIgnoreCase(request.getCategory()))
                    .findFirst()
                    .orElse(null);
        }

        if (cashFlowItem != null) {
            payment.setCashFlowItem(cashFlowItem);
            payment.setCategory(cashFlowItem.getName());
        } else {
            payment.setCategory(request.getCategory());
        }

        ReferenceCounterparty counterparty = null;

        if (request.getCounterpartyId() != null) {
            counterparty = counterpartyRepository.findById(request.getCounterpartyId())
                    .orElseThrow(() -> new RuntimeException("Контрагент не найден"));
        } else if (request.getCounterparty() != null && !request.getCounterparty().isBlank()) {
            counterparty = counterpartyRepository.findAll().stream()
                    .filter(item -> item.getName().equalsIgnoreCase(request.getCounterparty()))
                    .findFirst()
                    .orElse(null);
        }

        if (counterparty != null) {
            payment.setCounterpartyRef(counterparty);
            payment.setCounterparty(counterparty.getName());
        } else {
            payment.setCounterparty(request.getCounterparty());
        }

        PaymentCalendar saved = paymentRepository.save(payment);
        return mapToResponse(saved);
    }

    public List<PaymentResponse> getPaymentsByCompany(UUID companyId) {
        List<PaymentCalendar> payments = paymentRepository.findByCompanyId(companyId);
        return payments.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<PaymentResponse> getUpcomingPayments(UUID companyId) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusMonths(1);
        List<PaymentCalendar> payments = paymentRepository.findByCompanyIdAndDueDateBetween(
                companyId, today, endDate
        );
        return payments.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional
    public PaymentResponse updateStatus(UUID paymentId, String status) {
        PaymentCalendar payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Платеж не найден"));
        payment.setStatus(status);
        return mapToResponse(paymentRepository.save(payment));
    }

    @Transactional
    public void deletePayment(UUID id) {
        paymentRepository.deleteById(id);
    }

    public BigDecimal getOverdueTotal(UUID companyId) {
        BigDecimal overdue = paymentRepository.sumOverduePayments(companyId, LocalDate.now());
        return overdue != null ? overdue : BigDecimal.ZERO;
    }

    private PaymentResponse mapToResponse(PaymentCalendar payment) {
        PaymentResponse response = new PaymentResponse();
        response.setId(payment.getId());
        response.setTitle(payment.getTitle());
        response.setDescription(payment.getDescription());
        response.setAmount(payment.getAmount());
        response.setDueDate(payment.getDueDate());
        response.setStatus(payment.getStatus());
        response.setCategory(payment.getCategory());
        response.setCounterparty(payment.getCounterparty());
        response.setCompanyName(payment.getCompany().getName());
        response.setCreatedByName(
                payment.getCreatedBy() != null ? payment.getCreatedBy().getFullName() : null
        );
        response.setOverdue(
                payment.getDueDate().isBefore(LocalDate.now())
                        && !"PAID".equals(payment.getStatus())
        );

        if (payment.getCashFlowItem() != null) {
            response.setCashFlowItemId(payment.getCashFlowItem().getId());
        }

        if (payment.getCounterpartyRef() != null) {
            response.setCounterpartyId(payment.getCounterpartyRef().getId());
        }

        return response;
    }
}
