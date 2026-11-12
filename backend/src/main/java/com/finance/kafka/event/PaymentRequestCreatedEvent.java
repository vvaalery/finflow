package com.finance.kafka.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentRequestCreatedEvent(
        UUID id,
        String title,
        BigDecimal amount,
        String status,
        LocalDate date
) {
}
