package com.finance.kafka.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.entity.OutboxEvent;
import com.finance.backend.logging.CorrelationFilter;
import com.finance.backend.repository.OutboxEventRepository;
import com.finance.kafka.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String type, UUID aggregateId, UUID companyId, Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("payload события должен состоять из пар ключ-значение");
        }

        Map<String, Object> payload = new LinkedHashMap<>();

        for (int i = 0; i < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            if (value != null) {
                payload.put(String.valueOf(keyValues[i]),
                        value instanceof Number || value instanceof Boolean ? value : value.toString());
            }
        }

        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID().toString(), type, aggregateId.toString(),
                companyId == null ? null : companyId.toString(), MDC.get(CorrelationFilter.MDC_KEY),
                Instant.now().toString(), payload);

        try {
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setId(UUID.randomUUID());
            outboxEvent.setEventType(type);
            outboxEvent.setAggregateId(aggregateId);
            outboxEvent.setPayload(objectMapper.writeValueAsString(envelope));
            outboxEvent.setStatus("PENDING");
            outboxEvent.setAttempts(0);
            outboxEvent.setCreatedAt(LocalDateTime.now());
            outboxEvent.setNextAttemptAt(LocalDateTime.now());
            repository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать событие " + type, e);
        }
    }
}
