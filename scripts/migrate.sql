ALTER TABLE IF EXISTS cash_flow_forecasts ADD COLUMN IF NOT EXISTS scenario varchar(30) DEFAULT 'BASE';
ALTER TABLE IF EXISTS cash_flow_forecasts ADD COLUMN IF NOT EXISTS parameters jsonb;
CREATE INDEX IF NOT EXISTS idx_accounts_company_id ON accounts(company_id);
CREATE INDEX IF NOT EXISTS idx_budgets_company_id ON budgets(company_id);
CREATE INDEX IF NOT EXISTS idx_cash_flow_forecasts_company_id ON cash_flow_forecasts(company_id);
CREATE INDEX IF NOT EXISTS idx_companies_owner_id ON companies(owner_id);
CREATE INDEX IF NOT EXISTS idx_forecast_items_forecast_id ON forecast_items(forecast_id);
CREATE INDEX IF NOT EXISTS idx_integrations_user_id ON integrations(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_account_id ON transactions(account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(date);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON transactions(transaction_type);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user ON audit_logs(user_id);

CREATE TABLE IF NOT EXISTS notifications (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL,
    dedupe_key varchar(200) NOT NULL,
    type varchar(40) NOT NULL,
    severity varchar(20) NOT NULL,
    title varchar(200) NOT NULL,
    message text,
    link_tab varchar(40),
    is_read boolean NOT NULL DEFAULT false,
    dismissed boolean NOT NULL DEFAULT false,
    read_at timestamp,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notifications_user_key UNIQUE (user_id, dedupe_key)
);
CREATE INDEX IF NOT EXISTS idx_notifications_user_created ON notifications(user_id, created_at);
