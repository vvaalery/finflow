package com.finance.company;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.company.CompanyRepository.AccountRow;
import com.finance.company.CompanyRepository.CompanyRow;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AccountSnapshotConsumer implements Runnable {

    private static final Logger logger = Logger.getLogger(AccountSnapshotConsumer.class.getName());

    private static class InvalidMessageException extends Exception {
        InvalidMessageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record Snapshot(List<CompanyRow> companies, List<AccountRow> accounts) {}

    private final CompanyRepository repository;
    private final String bootstrapServers;
    private final String topic;
    private final String deadLetterTopic;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile boolean running = true;
    private volatile KafkaConsumer<String, String> consumer;

    public AccountSnapshotConsumer(CompanyRepository repository, String bootstrapServers, String topic) {
        this.repository = repository;
        this.bootstrapServers = bootstrapServers;
        this.topic = topic;
        this.deadLetterTopic = topic + ".DLT";
    }

    @Override
    public void run() {
        while (running) {
            try (KafkaConsumer<String, String> c = new KafkaConsumer<>(consumerProperties());
                 KafkaProducer<String, String> dlt = new KafkaProducer<>(producerProperties())) {
                this.consumer = c;
                c.subscribe(List.of(topic));
                logger.info("Подписка на топик " + topic + " (" + bootstrapServers + ")");
                while (running) {
                    ConsumerRecords<String, String> records = c.poll(Duration.ofSeconds(1));
                    if (!records.isEmpty()) {
                        handleBatch(c, dlt, records);
                    }
                }
            } catch (WakeupException e) {
            } catch (Exception e) {
                logger.log(Level.WARNING, "Ошибка Kafka-клиента загрузки счетов, повтор через 3 с: " + e.getMessage());
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
                repositoryApply(record.value());
            } catch (InvalidMessageException e) {
                if (!deadLetter(dlt, record, e.getMessage())) {
                    retryLater(c, records, done);
                    return;
                }
            } catch (SQLException e) {
                if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                    if (!deadLetter(dlt, record, "Нарушение целостности: " + e.getMessage())) {
                        retryLater(c, records, done);
                        return;
                    }
                } else {
                    logger.log(Level.WARNING, "БД company_db недоступна, offset не подтверждён: " + e.getMessage());
                    retryLater(c, records, done);
                    return;
                }
            }
            done.put(tp, new OffsetAndMetadata(record.offset() + 1));
        }
        commit(c, done);
    }

    private void retryLater(KafkaConsumer<String, String> c, ConsumerRecords<String, String> records,
                            Map<TopicPartition, OffsetAndMetadata> done) {
        for (TopicPartition tp : records.partitions()) {
            OffsetAndMetadata committed = done.get(tp);
            long next = committed != null ? committed.offset() : records.records(tp).get(0).offset();
            c.seek(tp, next);
        }
        commit(c, done);
        sleep(3000);
    }

    private void commit(KafkaConsumer<String, String> c, Map<TopicPartition, OffsetAndMetadata> done) {
        if (!done.isEmpty()) {
            c.commitSync(done);
        }
    }

    private boolean deadLetter(KafkaProducer<String, String> dlt, ConsumerRecord<String, String> record, String reason) {
        try {
            ProducerRecord<String, String> out = new ProducerRecord<>(deadLetterTopic, record.key(), record.value());
            out.headers().add("x-original-topic", record.topic().getBytes(StandardCharsets.UTF_8));
            out.headers().add("x-original-offset", Long.toString(record.offset()).getBytes(StandardCharsets.UTF_8));
            out.headers().add("x-error", String.valueOf(reason).getBytes(StandardCharsets.UTF_8));
            dlt.send(out).get(10, TimeUnit.SECONDS);
            logger.warning("Повреждённое сообщение перенесено в " + deadLetterTopic + ": " + reason);
            return true;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Не удалось отправить сообщение в DLT: " + e.getMessage());
            return false;
        }
    }

    private void repositoryApply(String json) throws InvalidMessageException, SQLException {
        Snapshot snapshot = parse(json);
        repository.bootstrap(snapshot.companies(), snapshot.accounts());
    }

    private Snapshot parse(String json) throws InvalidMessageException {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode companies = root.get("companies");
            JsonNode accounts = root.get("accounts");
            if (companies == null || !companies.isArray() || accounts == null || !accounts.isArray()) {
                throw new IllegalArgumentException("Нет массивов companies/accounts");
            }
            List<CompanyRow> cs = new ArrayList<>();
            for (JsonNode n : companies) {
                cs.add(new CompanyRow(uuid(n, "id"), text(n, "name"), optional(n, "taxId"), optional(n, "address"),
                        optional(n, "phone"), uuid(n, "ownerId"), optional(n, "description"),
                        optional(n, "websiteUrl"), optional(n, "logoUrl"),
                        n.hasNonNull("status") ? n.get("status").asText() : "ACTIVE"));
            }
            List<AccountRow> as = new ArrayList<>();
            for (JsonNode n : accounts) {
                as.add(new AccountRow(uuid(n, "id"), uuid(n, "companyId"), text(n, "accountName"),
                        text(n, "accountType"), text(n, "currency"),
                        new BigDecimal(text(n, "balance")),
                        !n.hasNonNull("isActive") || n.get("isActive").asBoolean()));
            }
            return new Snapshot(cs, as);
        } catch (Exception e) {
            throw new InvalidMessageException("Некорректный снимок: " + e.getMessage(), e);
        }
    }

    private static UUID uuid(JsonNode n, String field) {
        return UUID.fromString(text(n, field));
    }

    private static String text(JsonNode n, String field) {
        if (!n.hasNonNull(field)) {
            throw new IllegalArgumentException("Отсутствует поле " + field);
        }
        return n.get(field).asText();
    }

    private static String optional(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    private Properties consumerProperties() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "company-service-account-bootstrap");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "20");
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
