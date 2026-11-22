package com.finance.company;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

public class AccountReplicaStore {

    private final DatabaseConfig database;

    public AccountReplicaStore(DatabaseConfig database) {
        this.database = database;
    }

    public void init() throws SQLException {
        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS accounts_replica (
                        id UUID PRIMARY KEY,
                        company_id UUID NOT NULL,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_accounts_replica_company ON accounts_replica(company_id)");
        }
    }

    public void replaceAll(Map<UUID, UUID> accounts) throws SQLException {
        try (Connection connection = database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement upsert = connection.prepareStatement("""
                        INSERT INTO accounts_replica (id, company_id, updated_at)
                        VALUES (?, ?, CURRENT_TIMESTAMP)
                        ON CONFLICT (id) DO UPDATE
                        SET company_id = EXCLUDED.company_id, updated_at = CURRENT_TIMESTAMP
                        """)) {
                    for (Map.Entry<UUID, UUID> entry : accounts.entrySet()) {
                        upsert.setObject(1, entry.getKey());
                        upsert.setObject(2, entry.getValue());
                        upsert.addBatch();
                    }
                    upsert.executeBatch();
                }

                Array ids = connection.createArrayOf("uuid",
                        accounts.keySet().stream().map(UUID::toString).toArray());
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM accounts_replica WHERE NOT (id = ANY (?))")) {
                    delete.setArray(1, ids);
                    delete.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }
}
