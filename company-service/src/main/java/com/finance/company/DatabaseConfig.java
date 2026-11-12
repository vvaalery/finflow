package com.finance.company;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {

    private final String url;
    private final String username;
    private final String password;

    public DatabaseConfig() {
        this.url = System.getenv().getOrDefault(
                "DB_URL",
                "jdbc:postgresql://localhost:5432/finance_db"
        );
        this.username = System.getenv().getOrDefault("DB_USERNAME", "postgres");
        this.password = System.getenv().getOrDefault("DB_PASSWORD", "1111");
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}
