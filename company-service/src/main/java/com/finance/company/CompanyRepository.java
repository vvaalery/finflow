package com.finance.company;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

public class CompanyRepository {

    public record CompanyRow(UUID id, String name, String taxId, String address, String phone, UUID ownerId,
                             String description, String websiteUrl, String logoUrl, String status) {}

    public record AccountRow(UUID id, UUID companyId, String accountName, String accountType, String currency,
                             BigDecimal balance, boolean active) {}

    public record Ownership(boolean exists, boolean belongs) {}

    private static final String COMPANY_COLUMNS =
            "id, name, tax_id, address, phone, owner_id, description, website_url, logo_url, status";
    private static final String ACCOUNT_COLUMNS =
            "id, company_id, account_name, account_type, currency, balance, is_active";

    private final DatabaseConfig database;

    public CompanyRepository(DatabaseConfig database) {
        this.database = database;
    }

    public void init() throws SQLException {
        try (Connection cn = database.getConnection(); Statement st = cn.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS companies (
                        id UUID PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        tax_id VARCHAR(50) UNIQUE,
                        address TEXT,
                        phone VARCHAR(20),
                        owner_id UUID NOT NULL,
                        description TEXT,
                        website_url VARCHAR(255),
                        logo_url TEXT,
                        status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )""");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        id UUID PRIMARY KEY,
                        company_id UUID NOT NULL REFERENCES companies(id),
                        account_name VARCHAR(255) NOT NULL,
                        account_type VARCHAR(50) NOT NULL
                            CHECK (account_type IN ('CASH','BANK','CARD','ELECTRONIC')),
                        currency VARCHAR(3) NOT NULL DEFAULT 'BYN',
                        balance NUMERIC(15,2) NOT NULL DEFAULT 0,
                        is_active BOOLEAN NOT NULL DEFAULT TRUE,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_accounts_company ON accounts(company_id)");
            st.execute("""
                    CREATE TABLE IF NOT EXISTS balance_operations (
                        operation_id UUID PRIMARY KEY,
                        account_id UUID NOT NULL,
                        delta NUMERIC(15,2) NOT NULL,
                        balance_after NUMERIC(15,2) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )""");
        }
    }

    public Optional<CompanyRow> findCompany(UUID id) throws SQLException {
        try (Connection cn = database.getConnection();
             PreparedStatement ps = cn.prepareStatement("SELECT " + COMPANY_COLUMNS + " FROM companies WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(company(rs)) : Optional.empty();
            }
        }
    }

    public CompanyRow saveCompany(CompanyRow c) throws SQLException {
        String sql = """
                INSERT INTO companies (id, name, tax_id, address, phone, owner_id, description, website_url, logo_url, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name, tax_id = EXCLUDED.tax_id, address = EXCLUDED.address,
                    phone = EXCLUDED.phone, owner_id = EXCLUDED.owner_id, description = EXCLUDED.description,
                    website_url = EXCLUDED.website_url, logo_url = EXCLUDED.logo_url, status = EXCLUDED.status,
                    updated_at = CURRENT_TIMESTAMP
                """;
        try (Connection cn = database.getConnection(); PreparedStatement ps = cn.prepareStatement(sql)) {
            bind(ps, c);
            ps.executeUpdate();
        }
        return findCompany(c.id()).orElseThrow();
    }

    public boolean deleteCompany(UUID id) throws SQLException {
        try (Connection cn = database.getConnection();
             PreparedStatement ps = cn.prepareStatement("DELETE FROM companies WHERE id = ?")) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public Optional<AccountRow> findAccount(UUID id) throws SQLException {
        try (Connection cn = database.getConnection();
             PreparedStatement ps = cn.prepareStatement("SELECT " + ACCOUNT_COLUMNS + " FROM accounts WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(account(rs)) : Optional.empty();
            }
        }
    }

    public AccountRow saveAccount(AccountRow a) throws SQLException {
        String sql = """
                INSERT INTO accounts (id, company_id, account_name, account_type, currency, balance, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    account_name = EXCLUDED.account_name, account_type = EXCLUDED.account_type,
                    currency = EXCLUDED.currency, is_active = EXCLUDED.is_active,
                    updated_at = CURRENT_TIMESTAMP
                """;
        try (Connection cn = database.getConnection(); PreparedStatement ps = cn.prepareStatement(sql)) {
            bind(ps, a);
            ps.executeUpdate();
        }
        return findAccount(a.id()).orElseThrow();
    }

    public boolean deleteAccount(UUID id) throws SQLException {
        try (Connection cn = database.getConnection();
             PreparedStatement ps = cn.prepareStatement("DELETE FROM accounts WHERE id = ?")) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public Ownership ownership(UUID accountId, UUID companyId) throws SQLException {
        String sql = "SELECT company_id FROM accounts WHERE id = ?";
        try (Connection cn = database.getConnection(); PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setObject(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new Ownership(false, false);
                }
                return new Ownership(true, companyId.equals(rs.getObject(1, UUID.class)));
            }
        }
    }

    public BigDecimal changeBalance(UUID operationId, UUID accountId, BigDecimal delta) throws SQLException {
        try (Connection cn = database.getConnection()) {
            cn.setAutoCommit(false);
            try {
                int inserted;
                try (PreparedStatement ps = cn.prepareStatement("""
                        INSERT INTO balance_operations (operation_id, account_id, delta, balance_after)
                        VALUES (?, ?, ?, 0) ON CONFLICT (operation_id) DO NOTHING
                        """)) {
                    ps.setObject(1, operationId);
                    ps.setObject(2, accountId);
                    ps.setBigDecimal(3, delta);
                    inserted = ps.executeUpdate();
                }
                if (inserted == 0) {
                    try (PreparedStatement ps = cn.prepareStatement(
                            "SELECT balance_after FROM balance_operations WHERE operation_id = ?")) {
                        ps.setObject(1, operationId);
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            BigDecimal replay = rs.getBigDecimal(1);
                            cn.commit();
                            return replay;
                        }
                    }
                }
                BigDecimal after;
                try (PreparedStatement ps = cn.prepareStatement("""
                        UPDATE accounts SET balance = balance + ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? RETURNING balance
                        """)) {
                    ps.setBigDecimal(1, delta);
                    ps.setObject(2, accountId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new NoSuchElementException("Счёт не найден");
                        }
                        after = rs.getBigDecimal(1);
                    }
                }
                try (PreparedStatement ps = cn.prepareStatement(
                        "UPDATE balance_operations SET balance_after = ? WHERE operation_id = ?")) {
                    ps.setBigDecimal(1, after);
                    ps.setObject(2, operationId);
                    ps.executeUpdate();
                }
                cn.commit();
                return after;
            } catch (SQLException | RuntimeException e) {
                cn.rollback();
                throw e;
            }
        }
    }

    public List<AccountRow> activeAccounts(UUID companyId) throws SQLException {
        List<AccountRow> list = new ArrayList<>();
        try (Connection cn = database.getConnection();
             PreparedStatement ps = cn.prepareStatement(
                     "SELECT " + ACCOUNT_COLUMNS + " FROM accounts WHERE company_id = ? AND is_active ORDER BY created_at")) {
            ps.setObject(1, companyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(account(rs));
                }
            }
        }
        return list;
    }

    public void bootstrap(List<CompanyRow> companies, List<AccountRow> accounts) throws SQLException {
        try (Connection cn = database.getConnection()) {
            cn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = cn.prepareStatement("""
                        INSERT INTO companies (id, name, tax_id, address, phone, owner_id, description, website_url, logo_url, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                        """)) {
                    for (CompanyRow c : companies) {
                        bind(ps, c);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (PreparedStatement ps = cn.prepareStatement("""
                        INSERT INTO accounts (id, company_id, account_name, account_type, currency, balance, is_active)
                        VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING
                        """)) {
                    for (AccountRow a : accounts) {
                        bind(ps, a);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                cn.commit();
            } catch (SQLException | RuntimeException e) {
                cn.rollback();
                throw e;
            }
        }
    }

    private static void bind(PreparedStatement ps, CompanyRow c) throws SQLException {
        ps.setObject(1, c.id());
        ps.setString(2, c.name());
        ps.setString(3, c.taxId());
        ps.setString(4, c.address());
        ps.setString(5, c.phone());
        ps.setObject(6, c.ownerId());
        ps.setString(7, c.description());
        ps.setString(8, c.websiteUrl());
        ps.setString(9, c.logoUrl());
        ps.setString(10, c.status());
    }

    private static void bind(PreparedStatement ps, AccountRow a) throws SQLException {
        ps.setObject(1, a.id());
        ps.setObject(2, a.companyId());
        ps.setString(3, a.accountName());
        ps.setString(4, a.accountType());
        ps.setString(5, a.currency());
        ps.setBigDecimal(6, a.balance());
        ps.setBoolean(7, a.active());
    }

    private static CompanyRow company(ResultSet rs) throws SQLException {
        return new CompanyRow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("tax_id"),
                rs.getString("address"), rs.getString("phone"), rs.getObject("owner_id", UUID.class),
                rs.getString("description"), rs.getString("website_url"), rs.getString("logo_url"),
                rs.getString("status"));
    }

    private static AccountRow account(ResultSet rs) throws SQLException {
        return new AccountRow(rs.getObject("id", UUID.class), rs.getObject("company_id", UUID.class),
                rs.getString("account_name"), rs.getString("account_type"), rs.getString("currency"),
                rs.getBigDecimal("balance"), rs.getBoolean("is_active"));
    }
}
