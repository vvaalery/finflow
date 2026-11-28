package com.finance.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AuditEventConsumer implements Runnable {

    private static final Logger logger = Logger.getLogger(AuditEventConsumer.class.getName());

    public static final List<String> TOPICS = List.of(
            "finflow.user-created",
            "finflow.payment-request-created",
            "finflow.payment-request-approved",
            "finflow.payment-request-rejected",
            "finflow.payment-calendar-created",
            "finflow.transaction-created",
            "finflow.transactions-imported",
            "finflow.forecast-generated");

    private static class InvalidMessageException extends Exception {
        InvalidMessageException(String message) {
            super(message);
        }
    }

    private final AuditStore store;
    private final String bootstrapServers;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile boolean running = true;
    private volatile KafkaConsumer<String, String> consumer;

    public AuditEventConsumer(AuditStore store, String bootstrapServers) {
        this.store = store;
        this.bootstrapServers = bootstrapServers;
    }

    @Override
    public void run() {
        while (running) {
            try (KafkaConsumer<String, String> c = new KafkaConsumer<>(consumerProperties());
                 KafkaProducer<String, String> dlt = new KafkaProducer<>(producerProperties())) {
                this.consumer = c;
                c.subscribe(TOPICS);
                logger.info("Audit Service подписан на " + TOPICS);
                while (running) {
                    ConsumerRecords<String, String> records = c.poll(Duration.ofSeconds(1));
                    if (!records.isEmpty()) {
                        handleBatch(c, dlt, records);
                    }
                }
            } catch (WakeupException e) {
            } catch (Exception e) {
                logger.log(Level.WARNING, "Ошибка Kafka-клиента аудита, повтор через 3 с: " + e.getMessage());
                sleep(3000);
            }
        }
    }

    public void stop() {
        running = false;
        KafkaConsumer<String, String> c = consumer;
        if (c != null) {
            c.wakeup();
        }
    }

    private void handleBatch(KafkaConsumer<String, String> c, KafkaProducer<String, String> dlt,
                             ConsumerRecords<String, String> records) {
        Map<TopicPartition, OffsetAndMetadata> done = new HashMap<>();
        for (ConsumerRecord<String, String> record : records) {
            TopicPartition tp = new TopicPartition(record.topic(), record.partition());
            try {
                AuditStore.Event event = parse(record);
                boolean inserted = store.save(event);
                logger.info("[corr=" + event.correlationId() + "] Аудит: " + event.type() + " eventId="
                        + event.eventId() + (inserted ? " сохранён" : " уже был сохранён"));
            } catch (InvalidMessageException e) {
                if (!deadLetter(dlt, record, e.getMessage())) {
                    retryLater(c, records, done);
                    return;
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "audit_db недоступна, offset не подтверждён: " + e.getMessage());
                retryLater(c, records, done);
                return;
            }
            done.put(tp, new OffsetAndMetadata(record.offset() + 1));
        }
        c.commitSync(done);
    }

    private AuditStore.Event parse(ConsumerRecord<String, String> record) throws InvalidMessageException {
        try {
            JsonNode n = mapper.readTree(record.value());
            if (!n.hasNonNull("eventId") || !n.hasNonNull("type")) {
                throw new InvalidMessageException("Нет eventId или type");
            }
            return new AuditStore.Event(n.get("eventId").asText(), n.get("type").asText(),
                    text(n, "aggregateId"), text(n, "companyId"), text(n, "correlationId"), text(n, "occurredAt"),
                    n.has("payload") ? n.get("payload").toString() : "{}",
                    record.topic(), record.partition(), record.offset());
        } catch (InvalidMessageException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidMessageException("Некорректное событие: " + e.getMessage());
        }
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private void retryLater(KafkaConsumer<String, String> c, ConsumerRecords<String, String> records,
                            Map<TopicPartition, OffsetAndMetadata> done) {
        for (TopicPartition tp : records.partitions()) {
            OffsetAndMetadata committed = done.get(tp);
            c.seek(tp, committed != null ? committed.offset() : records.records(tp).get(0).offset());
        }
        if (!done.isEmpty()) {
            c.commitSync(done);
        }
        sleep(3000);
    }

    private boolean deadLetter(KafkaProducer<String, String> dlt, ConsumerRecord<String, String> record, String reason) {
        try {
            ProducerRecord<String, String> out =
                    new ProducerRecord<>(record.topic() + ".audit.DLT", record.key(), record.value());
            out.headers().add("x-error", String.valueOf(reason).getBytes(StandardCharsets.UTF_8));
            dlt.send(out).get(10, TimeUnit.SECONDS);
            logger.warning("Некорректное событие отправлено в DLT аудита: " + reason);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Не удалось отправить событие в DLT: " + e.getMessage());
            return false;
        }
    }

    private Properties consumerProperties() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "finflow-audit-service");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "50");
        return props;
    }

    private Properties producerProperties() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return props;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
