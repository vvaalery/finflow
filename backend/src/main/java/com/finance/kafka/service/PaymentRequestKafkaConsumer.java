package com.finance.kafka.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.backend.audit.AuditLog;
import com.finance.backend.audit.AuditLogRepository;
import com.finance.backend.repository.PaymentCalendarRepository;
import com.finance.backend.repository.PaymentRequestRepository;
import com.finance.kafka.config.PaymentRequestKafkaConfig;
import com.finance.kafka.event.PaymentCalendarCreatedEvent;
import com.finance.kafka.event.PaymentRequestCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRequestKafkaConsumer {

    private static final String ACTION = "KAFKA_EVENT_PROCESSED";

    private static final String PAYMENT_REQUEST_TYPE =
            "PAYMENT_REQUEST_CREATED";

    private static final String PAYMENT_CALENDAR_TYPE =
            "PAYMENT_CALENDAR_CREATED";

    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentCalendarRepository paymentCalendarRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = PaymentRequestKafkaConfig.TOPIC,
            groupId = "finflow-payment-group"
    )
    @Transactional
    public void consume(ConsumerRecord<String, Object> record) {
        Object event = record.value();

        if (event instanceof PaymentRequestCreatedEvent paymentRequestEvent) {
            processPaymentRequest(paymentRequestEvent);
            return;
        }

        if (event instanceof PaymentCalendarCreatedEvent paymentCalendarEvent) {
            processPaymentCalendar(paymentCalendarEvent);
            return;
        }

        throw new IllegalArgumentException(
                "Kafka: получено событие неизвестного типа: "
                        + (event == null ? "null" : event.getClass().getName())
        );
    }

    private void processPaymentRequest(PaymentRequestCreatedEvent event) {
        if (event.id() == null) {
            throw new IllegalArgumentException(
                    "Kafka: отсутствует ID платёжной заявки"
            );
        }

        String eventId = event.id().toString();

        if (isAlreadyProcessed(PAYMENT_REQUEST_TYPE, eventId)) {
            log.info(
                    "Kafka: событие создания заявки {} уже обработано",
                    eventId
            );
            return;
        }

        if (!paymentRequestRepository.existsById(event.id())) {
            throw new IllegalStateException(
                    "Kafka: заявка " + eventId + " не найдена в базе данных"
            );
        }

        saveAudit(PAYMENT_REQUEST_TYPE, eventId, event);

        log.info(
                "Kafka: событие создания заявки обработано: id={}, title={}, amount={}",
                event.id(),
                event.title(),
                event.amount()
        );
    }

    private void processPaymentCalendar(PaymentCalendarCreatedEvent event) {
        if (event.id() == null) {
            throw new IllegalArgumentException(
                    "Kafka: отсутствует ID календарного платежа"
            );
        }

        String eventId = event.id().toString();

        if (isAlreadyProcessed(PAYMENT_CALENDAR_TYPE, eventId)) {
            log.info(
                    "Kafka: событие создания календарного платежа {} уже обработано",
                    eventId
            );
            return;
        }

        if (!paymentCalendarRepository.existsById(event.id())) {
            throw new IllegalStateException(
                    "Kafka: календарный платеж " + eventId
                            + " не найден в базе данных"
            );
        }

        saveAudit(PAYMENT_CALENDAR_TYPE, eventId, event);

        log.info(
                "Kafka: событие создания календарного платежа обработано: id={}, title={}, amount={}",
                event.id(),
                event.title(),
                event.amount()
        );
    }

    private boolean isAlreadyProcessed(String entityType, String entityId) {
        return auditLogRepository.existsByActionAndEntityTypeAndEntityId(
                ACTION,
                entityType,
                entityId
        );
    }

    private void saveAudit(String entityType, String entityId, Object event) {
        AuditLog auditLog = new AuditLog();

        auditLog.setAction(ACTION);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setNewValue(objectMapper.valueToTree(event));

        auditLogRepository.save(auditLog);
    }

    @KafkaListener(
            topics = PaymentRequestKafkaConfig.DLT_TOPIC,
            groupId = "finflow-payment-dlt-group"
    )
    public void consumeDeadLetter(ConsumerRecord<String, Object> record) {
        Object event = record.value();

        if (event instanceof PaymentRequestCreatedEvent paymentRequestEvent) {
            log.error(
                    "Kafka DLT: заявка не обработана после повторных попыток: id={}, title={}",
                    paymentRequestEvent.id(),
                    paymentRequestEvent.title()
            );
            return;
        }

        if (event instanceof PaymentCalendarCreatedEvent paymentCalendarEvent) {
            log.error(
                    "Kafka DLT: календарный платеж не обработан после повторных попыток: id={}, title={}",
                    paymentCalendarEvent.id(),
                    paymentCalendarEvent.title()
            );
            return;
        }

        log.error(
                "Kafka DLT: получено событие неизвестного типа: {}",
                event == null ? "null" : event.getClass().getName()
        );
    }
}