package com.finance.kafka.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeadLetterLogger {

    @KafkaListener(topics = "#{T(com.finance.kafka.event.EventTypes).dltTopics()}",
            groupId = "finflow-dlt-group")
    public void consume(ConsumerRecord<String, Object> record) {
        log.error("Kafka DLT: событие не обработано после повторов: topic={}, key={}, value={}",
                record.topic(), record.key(), record.value());
    }
}
