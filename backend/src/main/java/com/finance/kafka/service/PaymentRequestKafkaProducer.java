package com.finance.kafka.service;

import com.finance.kafka.config.PaymentRequestKafkaConfig;
import com.finance.kafka.event.PaymentRequestCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRequestKafkaProducer {

    private final KafkaTemplate<String, PaymentRequestCreatedEvent> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(PaymentRequestCreatedEvent event) {
        kafkaTemplate.send(
                PaymentRequestKafkaConfig.TOPIC,
                event.id().toString(),
                event
        ).whenComplete((result, exception) -> {
            if (exception != null) {
                log.error("Kafka: ошибка отправки события о платёжной заявке {}", event.id(), exception);
            } else {
                log.info(
                        "Kafka: событие о платёжной заявке {} отправлено, partition={}, offset={}",
                        event.id(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                );
            }
        });
    }
}
