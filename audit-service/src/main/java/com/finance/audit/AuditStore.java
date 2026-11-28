package com.finance.audit;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;

public class AuditStore {

    public record Event(String eventId, String type, String aggregateId, String companyId, String correlationId,
                        String occurredAt, String payloadJson, String topic, int partition, long offset) {}

    private final String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5434/audit_db");
    private final String user = System.getenv().getOrDefault("DB_USERNAME", "postgres");
    private final String password = System.getenv().getOrDefault("DB_PASSWORD", "1111");

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public void init() throws SQLException {
        try (Connection cn = connection(); Statement st = cn.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS audit_events (
                        event_id TEXT PRIMARY KEY,
                        event_type VARCHAR(100) NOT NULL,
                        aggregate_id TEXT,
                        company_id TEXT,
                        correlation_id TEXT,
                        occurred_at TIMESTAMPTZ,
                        payload JSONB,
                        topic TEXT NOT NULL,
                        kafka_partition INT NOT NULL,
                        kafka_offset BIGINT NOT NULL,
                        received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_audit_events_corr ON audit_events(correlation_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_audit_events_company ON audit_events(company_id, occurred_at)");
        }
    }

    public boolean save(Event e) throws SQLException {
        String sql = """
                INSERT INTO audit_events (event_id, event_type, aggregate_id, company_id, correlation_id,
                    occurred_at, payload, topic, kafka_partition, kafka_offset)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """;
        try (Connection cn = connection(); PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, e.eventId());
            ps.setString(2, e.type());
            ps.setString(3, e.aggregateId());
            ps.setString(4, e.companyId());
            ps.setString(5, e.correlationId());
            ps.setObject(6, e.occurredAt() == null ? null : OffsetDateTime.ofInstant(
                    Instant.parse(e.occurredAt()), java.time.ZoneOffset.UTC));
            ps.setString(7, e.payloadJson());
            ps.setString(8, e.topic());
            ps.setInt(9, e.partition());
            ps.setLong(10, e.offset());
            return ps.executeUpdate() == 1;
        }
    }
}
