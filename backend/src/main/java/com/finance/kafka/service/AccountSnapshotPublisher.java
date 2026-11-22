package com.finance.kafka.service;

import com.finance.backend.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountSnapshotPublisher {
    public static final String TOPIC = "finflow.accounts.snapshot";

    private final AccountRepository accounts;
    private final KafkaTemplate<String, Object> kafka;

    @Scheduled(fixedDelayString = "${app.account-snapshot.interval-ms:3000}", initialDelay = 5000)
    @Transactional(readOnly = true)
    public void publish() {
        try {
            List<Map<String, String>> list = accounts.findAll().stream()
                    .map(a -> Map.of("id", a.getId().toString(), "companyId", a.getCompany().getId().toString()))
                    .toList();
            kafka.send(TOPIC, "accounts", Map.of("accounts", list)).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Снимок счетов не отправлен в Kafka: {}", e.getMessage());
        }
    }
}
