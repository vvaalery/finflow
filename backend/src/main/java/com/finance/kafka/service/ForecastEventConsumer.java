package com.finance.kafka.service;

import com.finance.backend.logging.CorrelationFilter;
import com.finance.kafka.event.EventEnvelope;
import com.finance.kafka.event.EventTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ForecastEventConsumer {

    private static final String CONSUMER = "forecast";

    private final ProcessedEventStore processed;
    private final ForecastRefreshService refresh;

    @KafkaListener(
            topics = {EventTypes.TOPIC_TRANSACTION_CREATED, EventTypes.TOPIC_TRANSACTIONS_IMPORTED,
                    EventTypes.TOPIC_PAYMENT_REQUEST_APPROVED},
            groupId = "finflow-forecast-group")
    @Transactional
    public void consume(ConsumerRecord<String, Object> record) {
        if (!(record.value() instanceof EventEnvelope event)) {
            throw new IllegalArgumentException("Прогнозирование: неожиданный тип события "
                    + (record.value() == null ? "null" : record.value().getClass().getName()));
        }

        if (event.correlationId() != null) {
            MDC.put(CorrelationFilter.MDC_KEY, event.correlationId());
        }

        try {
            if (event.eventId() == null || event.companyId() == null) {
                throw new IllegalArgumentException("В событии " + event.type() + " нет eventId или companyId");
            }

            if (!processed.markIfNew(CONSUMER, event.eventId())) {
                log.info("Прогнозирование: событие {} уже обработано", event.eventId());
                return;
            }

            log.info("Прогнозирование: получено {} (eventId={}, companyId={})",
                    event.type(), event.eventId(), event.companyId());

            refresh.refreshLatest(UUID.fromString(event.companyId()));
        } finally {
            MDC.remove(CorrelationFilter.MDC_KEY);
        }
    }
}
