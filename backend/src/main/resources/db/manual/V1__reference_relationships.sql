BEGIN;

INSERT INTO reference_cfo (id, name, description)
SELECT gen_random_uuid(), values_table.name, values_table.description
FROM (
    VALUES
        ('Администрация', 'Административное управление компанией'),
        ('Дизайн и разработка', 'Разработка и дизайн проектов'),
        ('Продажи и маркетинг', 'Продажи и продвижение услуг')
) AS values_table(name, description)
WHERE NOT EXISTS (
    SELECT 1
    FROM reference_cfo existing
    WHERE existing.name = values_table.name
);

INSERT INTO reference_cash_flow_items (id, name, type, description)
SELECT gen_random_uuid(), values_table.name, values_table.type, values_table.description
FROM (
    VALUES
        ('Выручка', 'INCOME', 'Поступления от клиентов и проектов'),
        ('Операционные расходы', 'EXPENSE', 'Текущие расходы компании')
) AS values_table(name, type, description)
WHERE NOT EXISTS (
    SELECT 1
    FROM reference_cash_flow_items existing
    WHERE existing.name = values_table.name
      AND existing.type = values_table.type
);

INSERT INTO reference_counterparties (id, name)
SELECT gen_random_uuid(), values_table.name
FROM (
    VALUES
        ('Корпоративные клиенты'),
        ('Поставщики и подрядчики')
) AS values_table(name)
WHERE NOT EXISTS (
    SELECT 1
    FROM reference_counterparties existing
    WHERE existing.name = values_table.name
);

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS cash_flow_item_id UUID,
    ADD COLUMN IF NOT EXISTS counterparty_id UUID;

ALTER TABLE payment_calendar
    ADD COLUMN IF NOT EXISTS cash_flow_item_id UUID,
    ADD COLUMN IF NOT EXISTS counterparty_id UUID;

CREATE INDEX IF NOT EXISTS idx_transactions_cash_flow_item_id
    ON transactions(cash_flow_item_id);

CREATE INDEX IF NOT EXISTS idx_transactions_counterparty_id
    ON transactions(counterparty_id);

CREATE INDEX IF NOT EXISTS idx_payment_calendar_cash_flow_item_id
    ON payment_calendar(cash_flow_item_id);

CREATE INDEX IF NOT EXISTS idx_payment_calendar_counterparty_id
    ON payment_calendar(counterparty_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'transactions_cash_flow_item_id_fkey'
    ) THEN
        ALTER TABLE transactions
            ADD CONSTRAINT transactions_cash_flow_item_id_fkey
            FOREIGN KEY (cash_flow_item_id)
            REFERENCES reference_cash_flow_items(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'transactions_counterparty_id_fkey'
    ) THEN
        ALTER TABLE transactions
            ADD CONSTRAINT transactions_counterparty_id_fkey
            FOREIGN KEY (counterparty_id)
            REFERENCES reference_counterparties(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'payment_calendar_cash_flow_item_id_fkey'
    ) THEN
        ALTER TABLE payment_calendar
            ADD CONSTRAINT payment_calendar_cash_flow_item_id_fkey
            FOREIGN KEY (cash_flow_item_id)
            REFERENCES reference_cash_flow_items(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'payment_calendar_counterparty_id_fkey'
    ) THEN
        ALTER TABLE payment_calendar
            ADD CONSTRAINT payment_calendar_counterparty_id_fkey
            FOREIGN KEY (counterparty_id)
            REFERENCES reference_counterparties(id);
    END IF;
END $$;

UPDATE transactions t
SET cash_flow_item_id = r.id
FROM reference_cash_flow_items r
WHERE t.category = r.name
  AND t.transaction_type = r.type
  AND t.cash_flow_item_id IS NULL;

UPDATE transactions t
SET counterparty_id = r.id
FROM reference_counterparties r
WHERE t.counterparty = r.name
  AND t.counterparty_id IS NULL;

UPDATE payment_calendar p
SET cash_flow_item_id = r.id
FROM reference_cash_flow_items r
WHERE p.category = r.name
  AND p.cash_flow_item_id IS NULL;

UPDATE payment_calendar p
SET counterparty_id = r.id
FROM reference_counterparties r
WHERE p.counterparty = r.name
  AND p.counterparty_id IS NULL;

COMMIT;
