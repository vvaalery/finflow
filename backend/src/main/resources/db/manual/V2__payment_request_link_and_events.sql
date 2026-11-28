ALTER TABLE payment_calendar ADD COLUMN IF NOT EXISTS payment_request_id uuid;

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_calendar_payment_request
    ON payment_calendar (payment_request_id)
    WHERE payment_request_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_payment_calendar_payment_request') THEN
        ALTER TABLE payment_calendar
            ADD CONSTRAINT fk_payment_calendar_payment_request
            FOREIGN KEY (payment_request_id) REFERENCES payment_requests (id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS processed_events (
    consumer     varchar(100) NOT NULL,
    event_id     varchar(100) NOT NULL,
    processed_at timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (consumer, event_id)
);
