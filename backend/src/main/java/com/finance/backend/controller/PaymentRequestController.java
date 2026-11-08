package com.finance.backend.controller;

import com.finance.backend.entity.PaymentRequest;
import com.finance.backend.entity.PaymentCalendar;
import com.finance.backend.repository.PaymentRequestRepository;
import com.finance.backend.repository.PaymentCalendarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@PreAuthorize("hasAnyRole('ADMIN','FINANCIAL_MANAGER')")
@RestController
@RequestMapping("/api/payment-requests")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PaymentRequestController {

    private final PaymentRequestRepository repository;
    private final PaymentCalendarRepository calendarRepository;

    @GetMapping
    public List<PaymentRequest> getAll() {
        List<PaymentRequest> requests = repository.findAll();
        
        Set<String> existingKeys = requests.stream()
                .map(r -> r.getTitle() + "_" + r.getAmount() + "_" + r.getDate())
                .collect(Collectors.toSet());

        List<PaymentCalendar> calendarItems = calendarRepository.findAll();
        
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
@Transactional
public PaymentRequest updateStatus(@PathVariable UUID id, @RequestParam String status) {
    try {
        PaymentCalendar calItem = calendarRepository.findById(id).orElse(null);
        
        PaymentRequest request;

        if (calItem != null) {
            System.out.println(">>> Синхронизация: Найдена запись в календаре. Обновляем статус.");
            
            calItem.setStatus(status);
            calendarRepository.save(calItem);

            request = repository.findAll().stream()
                .filter(r -> r.getTitle().equals(calItem.getTitle()) && 
                             r.getAmount().compareTo(calItem.getAmount()) == 0 &&
                             r.getDate().equals(calItem.getDueDate()))
                .findFirst()
                .orElse(new PaymentRequest());

            if (request.getId() == null) {
                request.setTitle(calItem.getTitle());
                request.setAmount(calItem.getAmount());
                request.setDate(calItem.getDueDate());
                request.setCategory(calItem.getCategory());
                request.setCounterparty(calItem.getCounterparty());
                request.setRequester("Система (Календарь)");
                request.setDescription("Одобрено из календаря");
            }
        } else {
            request = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Заявка с ID " + id + " не найдена"));
            
            final String title = request.getTitle();
            final BigDecimal amount = request.getAmount();
            final LocalDate date = request.getDate();

            calendarRepository.findAll().stream()
                .filter(c -> c.getTitle().equals(title) && 
                             c.getAmount().compareTo(amount) == 0 && 
                             c.getDueDate().equals(date))
                .findFirst()
                .ifPresent(c -> {
                    c.setStatus(status);
                    calendarRepository.save(c);
                });
        }

        request.setStatus(status);
        return repository.save(request);

    } catch (Exception e) {
        System.err.println("!!! Ошибка при обновлении: " + e.getMessage());
        throw e;
    }
}
}