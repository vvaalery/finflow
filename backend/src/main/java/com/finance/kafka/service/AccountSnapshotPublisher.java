package com.finance.kafka.service;

import com.finance.backend.entity.Account;
import com.finance.backend.entity.Company;
import com.finance.backend.repository.AccountRepository;
import com.finance.backend.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.account-snapshot.enabled", havingValue = "true", matchIfMissing = true)
public class AccountSnapshotPublisher {
    public static final String TOPIC = "finflow.accounts.snapshot";

    private final CompanyRepository companies;
    private final AccountRepository accounts;
    private final KafkaTemplate<String, Object> kafka;

    @Scheduled(fixedDelayString = "${app.account-snapshot.interval-ms:60000}", initialDelay = 10000)
    @Transactional(readOnly = true)
    public void publish() {
        try {
            List<Map<String, Object>> companyList = companies.findAll().stream().map(this::company).toList();
            List<Map<String, Object>> accountList = accounts.findAll().stream().map(this::account).toList();
            kafka.send(TOPIC, "snapshot", Map.of("companies", companyList, "accounts", accountList))
                    .get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Снимок компаний и счетов не отправлен в Kafka: {}", e.getMessage());
        }
    }

    private Map<String, Object> company(Company c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId().toString());
        m.put("name", c.getName());
        m.put("taxId", c.getTaxId());
        m.put("address", c.getAddress());
        m.put("phone", c.getPhone());
        m.put("ownerId", c.getOwner().getId().toString());
        m.put("description", c.getDescription());
        m.put("websiteUrl", c.getWebsiteUrl());
        m.put("logoUrl", c.getLogoUrl());
        m.put("status", c.getStatus());
        return m;
    }

    private Map<String, Object> account(Account a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("companyId", a.getCompany().getId().toString());
        m.put("accountName", a.getAccountName());
        m.put("accountType", a.getAccountType());
        m.put("currency", a.getCurrency());
        m.put("balance", a.getBalance().toPlainString());
        m.put("isActive", !Boolean.FALSE.equals(a.getIsActive()));
        return m;
    }
}
