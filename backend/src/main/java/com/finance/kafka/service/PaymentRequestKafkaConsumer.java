package com.finance.kafka.service;

import com.finance.kafka.config.PaymentRequestKafkaConfig;
import com.finance.kafka.event.PaymentRequestCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentRequestKafkaConsumer {

    @KafkaListener(
            topics = PaymentRequestKafkaConfig.TOPIC,
            groupId = "finflow-payment-group"
    )
    public void consume(PaymentRequestCreatedEvent event) {
        log.info(
                "Kafka: получено событие о платёжной заявке: id={}, title={}, amount={}, status={}",
                event.id(),
                event.title(),
                event.amount(),
                event.status()
        );
    }

    @KafkaListener(
            topics = PaymentRequestKafkaConfig.DLT_TOPIC,
            groupId = "finflow-payment-dlt-group"
    )
    public void consumeDeadLetter(PaymentRequestCreatedEvent event) {
        log.error(
                "Kafka DLT: событие не удалось обработать после повторных попыток: id={}, title={}",
                event.id(),
                event.title()
        );
    }
}