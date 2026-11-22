package com.finance.company;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AccountSnapshotConsumer implements Runnable {

    private static final Logger logger = Logger.getLogger(AccountSnapshotConsumer.class.getName());

    private final AccountReplicaStore store;
    private final String bootstrapServers;
    private final String topic;
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile boolean running = true;
    private volatile KafkaConsumer<String, String> consumer;

    public AccountSnapshotConsumer(AccountReplicaStore store, String bootstrapServers, String topic) {
        this.store = store;
        this.bootstrapServers = bootstrapServers;
        this.topic = topic;
    }

    @Override
    public void run() {
        while (running) {
            try (KafkaConsumer<String, String> c = new KafkaConsumer<>(properties())) {
                this.consumer = c;
                c.subscribe(List.of(topic));
                logger.info("Подписка на топик " + topic + " (" + bootstrapServers + ")");
                while (running) {
                    ConsumerRecords<String, String> records = c.poll(Duration.ofSeconds(1));
                    for (ConsumerRecord<String, String> record : records) {
                        apply(record.value());
                    }
                }
            } catch (WakeupException e) {
            } catch (Exception e) {
                logger.log(Level.WARNING, "Ошибка репликации счетов, повтор через 3 с: " + e.getMessage());
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

    private void apply(String json) throws Exception {
        JsonNode accounts = mapper.readTree(json).get("accounts");
        if (accounts == null || !accounts.isArray()) {
            return;
        }

        Map<UUID, UUID> snapshot = new HashMap<>();
        for (JsonNode node : accounts) {
            snapshot.put(
                    UUID.fromString(node.get("id").asText()),
                    UUID.fromString(node.get("companyId").asText())
            );
        }
        store.replaceAll(snapshot);
    }

    private Properties properties() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "company-service-account-replica");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
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
