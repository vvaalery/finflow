package com.finance.kafka.outbox;

import com.finance.backend.entity.OutboxEvent;
import com.finance.backend.repository.OutboxEventRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.kafka.event.PaymentRequestCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(PaymentRequestCreatedEvent event) {
        OutboxEvent outboxEvent = new OutboxEvent();

        try {
            outboxEvent.setId(UUID.randomUUID());
            outboxEvent.setEventType("PAYMENT_REQUEST_CREATED");
            outboxEvent.setAggregateId(event.id());
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
            outboxEvent.setStatus("PENDING");
            outboxEvent.setAttempts(0);
            outboxEvent.setCreatedAt(LocalDateTime.now());
            outboxEvent.setNextAttemptAt(LocalDateTime.now());

            repository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Не удалось сериализовать событие платёжной заявки",
                    e
            );
        }
    }
}
