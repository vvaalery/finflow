package com.finance.forecast.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.forecast.config.CorrelationFilter;
import com.finance.forecast.service.ForecastOrchestrator;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ForecastEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(ForecastEventConsumer.class);

    private final ForecastOrchestrator orchestrator;
    private final ObjectMapper mapper;

    @KafkaListener(topics = {
            "finflow.transaction-created",
            "finflow.transaction-updated",
            "finflow.transaction-deleted",
            "finflow.transactions-imported",
            "finflow.payment-request-approved"})
    public void on(ConsumerRecord<String, String> record) throws Exception {
        JsonNode event = mapper.readTree(record.value());
        String eventId = text(event, "eventId");
        String correlationId = text(event, "correlationId");

        if (correlationId != null) {
            MDC.put(CorrelationFilter.MDC_KEY, correlationId);
        }

        try {
            if (eventId == null) {
                throw new IllegalArgumentException("В событии нет eventId");
            }

            Set<String> companies = new LinkedHashSet<>();
            String companyId = text(event, "companyId");
            if (companyId != null) companies.add(companyId);

            String previous = text(event.path("payload"), "previousCompanyId");
            if (previous != null) companies.add(previous);

            if (companies.isEmpty()) {
                throw new IllegalArgumentException("В событии " + eventId + " нет companyId");
            }

            log.info("Получено {} (eventId={}), компании={}",
                    text(event, "type"), eventId, companies);

            for (String company : companies) {
                orchestrator.refreshFromEvent(UUID.fromString(company), eventId + ":" + company);
            }
        } finally {
            MDC.remove(CorrelationFilter.MDC_KEY);
        }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
