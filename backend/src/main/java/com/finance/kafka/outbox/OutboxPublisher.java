package com.finance.kafka.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.entity.OutboxEvent;
import com.finance.backend.logging.CorrelationFilter;
import com.finance.backend.repository.OutboxEventRepository;
import com.finance.kafka.event.EventEnvelope;
import com.finance.kafka.event.EventTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events =
                repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        "PENDING", LocalDateTime.now());

        for (OutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        String correlationId = null;

        try {
            EventEnvelope envelope = objectMapper.readValue(event.getPayload(), EventEnvelope.class);

            if (envelope.eventId() == null || envelope.type() == null
                    || !EventTypes.ALL.contains(envelope.type())) {
                event.setStatus("FAILED");
                event.setLastError("Событие Outbox в устаревшем или неизвестном формате");
                log.error("Outbox: событие {} типа {} помечено FAILED (неизвестный формат)",
                        event.getId(), event.getEventType());
                repository.save(event);
                return;
            }

            correlationId = envelope.correlationId();

            if (correlationId != null) {
                MDC.put(CorrelationFilter.MDC_KEY, correlationId);
            }

            ProducerRecord<String, Object> record = new ProducerRecord<>(
                    EventTypes.topic(envelope.type()), event.getAggregateId().toString(), envelope);

            if (correlationId != null) {
                record.headers().add("x-correlation-id", correlationId.getBytes(StandardCharsets.UTF_8));
            }

            kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);

            event.setStatus("PUBLISHED");
            event.setPublishedAt(LocalDateTime.now());
            event.setLastError(null);

            log.info("Outbox: событие {} ({}) опубликовано в {}, aggregateId={}",
                    envelope.eventId(), envelope.type(), record.topic(), event.getAggregateId());

        } catch (Exception e) {
            int attempts = event.getAttempts() + 1;
            long delaySeconds = Math.min(300L, 5L * (1L << Math.min(attempts - 1, 6)));

            event.setAttempts(attempts);
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));

            String error = e.getMessage();
            if (error == null || error.isBlank()) {
                error = e.getClass().getSimpleName();
            }

            event.setLastError(error.length() > 4000 ? error.substring(0, 4000) : error);

            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            log.error("Outbox: не удалось отправить событие {}, попытка {}, следующая через {} сек.",
                    event.getId(), attempts, delaySeconds, e);
        } finally {
            if (correlationId != null) {
                MDC.remove(CorrelationFilter.MDC_KEY);
            }
        }

        repository.save(event);
    }
}
