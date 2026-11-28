package com.finance.forecast.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProcessedEventStore {

    private final JdbcTemplate jdbc;

    public boolean exists(String key) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM processed_events WHERE event_key = ?",
                Integer.class,
                key
        );

        return count != null && count > 0;
    }

    public void mark(String key) {
        jdbc.update(
                "INSERT INTO processed_events (event_key) VALUES (?)",
                key
        );
    }
}
