package com.finance.kafka.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentCalendarCreatedEvent(
        UUID paymentId,
        UUID companyId,
        String title,
        BigDecimal amount,
        String status,
        LocalDate dueDate
) {}
