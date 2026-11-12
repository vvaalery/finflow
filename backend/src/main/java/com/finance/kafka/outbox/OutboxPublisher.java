package com.finance.kafka.outbox;

import com.finance.backend.entity.OutboxEvent;
import com.finance.backend.repository.OutboxEventRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.kafka.config.PaymentRequestKafkaConfig;
import com.finance.kafka.event.PaymentRequestCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, PaymentRequestCreatedEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events =
                repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        "PENDING",
                        LocalDateTime.now()
                );

        for (OutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEvent event) {
        try {
            PaymentRequestCreatedEvent payload =
                    objectMapper.readValue(
                            event.getPayload(),
                            PaymentRequestCreatedEvent.class
                    );

            kafkaTemplate.send(
                    PaymentRequestKafkaConfig.TOPIC,
                    event.getAggregateId().toString(),
                    payload
            ).get(10, TimeUnit.SECONDS);

            event.setStatus("PUBLISHED");
            event.setPublishedAt(LocalDateTime.now());
            event.setLastError(null);

            log.info(
                    "Outbox: событие {} опубликовано в Kafka",
                    event.getId()
            );

        } catch (Exception e) {
            int attempts = event.getAttempts() + 1;
            long delaySeconds = Math.min(
                    300L,
                    5L * (1L << Math.min(attempts - 1, 6))
            );

            event.setAttempts(attempts);
            event.setNextAttemptAt(
                    LocalDateTime.now().plusSeconds(delaySeconds)
            );

            String error = e.getMessage();
            if (error == null || error.isBlank()) {
                error = e.getClass().getSimpleName();
            }

            event.setLastError(
                    error.length() > 4000
                            ? error.substring(0, 4000)
                            : error
            );

            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            log.error(
                    "Outbox: не удалось отправить событие {}, попытка {}, следующая попытка через {} сек.",
                    event.getId(),
                    attempts,
                    delaySeconds,
                    e
            );
        }

        repository.save(event);
    }
}
