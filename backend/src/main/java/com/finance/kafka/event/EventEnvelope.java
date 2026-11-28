package com.finance.kafka.event;

import java.util.Map;

public record EventEnvelope(
        String eventId,
        String type,
        String aggregateId,
        String companyId,
        String correlationId,
        String occurredAt,
        Map<String, Object> payload
) {
}
