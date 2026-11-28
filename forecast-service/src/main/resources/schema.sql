CREATE TABLE IF NOT EXISTS cash_flow_forecasts (
    id uuid PRIMARY KEY,
    company_id uuid NOT NULL,
    forecast_period_start date NOT NULL,
    forecast_period_end date NOT NULL,
    total_projected_inflow numeric(15,2) DEFAULT 0,
    total_projected_outflow numeric(15,2) DEFAULT 0,
    net_cash_flow numeric(15,2) DEFAULT 0,
    strategy_used varchar(100),
    scenario varchar(30) DEFAULT 'BASE',
    parameters jsonb,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cash_flow_forecasts_company_created
    ON cash_flow_forecasts (company_id, created_at);

CREATE TABLE IF NOT EXISTS forecast_items (
    id uuid PRIMARY KEY,
    forecast_id uuid NOT NULL REFERENCES cash_flow_forecasts (id) ON DELETE CASCADE,
    forecast_date date NOT NULL,
    projected_inflow numeric(15,2) DEFAULT 0,
    projected_outflow numeric(15,2) DEFAULT 0,
    actual_inflow numeric(15,2) DEFAULT 0,
    actual_outflow numeric(15,2) DEFAULT 0,
    created_at timestamp DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_forecast_items_forecast_id ON forecast_items (forecast_id);

CREATE TABLE IF NOT EXISTS processed_events (
    event_key varchar(150) PRIMARY KEY,
    processed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id uuid PRIMARY KEY,
    topic varchar(150) NOT NULL,
    event_key varchar(100) NOT NULL,
    payload text NOT NULL,
    status varchar(30) NOT NULL DEFAULT 'PENDING',
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error text
);

CREATE INDEX IF NOT EXISTS idx_forecast_outbox_pending
    ON outbox_events (status, next_attempt_at);
