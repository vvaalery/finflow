package com.finance.forecast.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.forecast.config.CorrelationFilter;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class ForecastOutbox {
    private static final Logger log = LoggerFactory.getLogger(ForecastOutbox.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final KafkaTemplate<String, String> kafka;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(
            String type,
            UUID aggregateId,
            UUID companyId,
            Map<String, Object> payload
    ) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("type", type);
        envelope.put("aggregateId", aggregateId.toString());
        envelope.put("companyId", companyId == null ? null : companyId.toString());
        envelope.put("correlationId", MDC.get(CorrelationFilter.MDC_KEY));
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("payload", payload);

        try {
            jdbc.update(
                    "INSERT INTO outbox_events (id, topic, event_key, payload) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(),
                    "finflow." + type.toLowerCase(Locale.ROOT).replace('_', '-'),
                    aggregateId.toString(),
                    mapper.writeValueAsString(envelope)
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать событие " + type, e);
        }
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPending() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, topic, event_key, payload, attempts
                FROM outbox_events
                WHERE status = 'PENDING'
                  AND next_attempt_at <= CURRENT_TIMESTAMP
                ORDER BY created_at
                LIMIT 50
                FOR UPDATE SKIP LOCKED
                """);

        for (Map<String, Object> row : rows) {
            UUID id = (UUID) row.get("id");
            String payload = (String) row.get("payload");

            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        (String) row.get("topic"),
                        (String) row.get("event_key"),
                        payload
                );

                String correlation = mapper.readTree(payload)
                        .path("correlationId")
                        .asText(null);

                if (correlation != null) {
                    record.headers().add(
                            "x-correlation-id",
                            correlation.getBytes(StandardCharsets.UTF_8)
                    );
                    MDC.put(CorrelationFilter.MDC_KEY, correlation);
                }

                kafka.send(record).get(10, TimeUnit.SECONDS);

                jdbc.update(
                        "UPDATE outbox_events SET status = 'PUBLISHED', last_error = NULL WHERE id = ?",
                        id
                );

                log.info("Outbox: событие {} опубликовано в {}", id, row.get("topic"));
            } catch (Exception e) {
                int attempts = ((Number) row.get("attempts")).intValue() + 1;
                double delay = Math.min(300, 5L << Math.min(attempts - 1, 6));

                String error = e.getMessage() == null
                        ? e.getClass().getSimpleName()
                        : e.getMessage();

                jdbc.update("""
                        UPDATE outbox_events
                        SET attempts = ?,
                            last_error = ?,
                            next_attempt_at = CURRENT_TIMESTAMP + make_interval(secs => ?)
                        WHERE id = ?
                        """,
                        attempts,
                        error.length() > 4000 ? error.substring(0, 4000) : error,
                        delay,
                        id
                );

                log.error("Outbox: событие {} не отправлено, попытка {}", id, attempts, e);

                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                MDC.remove(CorrelationFilter.MDC_KEY);
            }
        }
    }
}
