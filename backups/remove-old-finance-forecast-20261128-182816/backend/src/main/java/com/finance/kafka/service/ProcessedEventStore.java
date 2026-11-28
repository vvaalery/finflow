package com.finance.kafka.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProcessedEventStore {

    private final JdbcTemplate jdbc;

    public boolean markIfNew(String consumer, String eventId) {
        return jdbc.update("INSERT INTO processed_events (consumer, event_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                consumer, eventId) == 1;
    }
}
