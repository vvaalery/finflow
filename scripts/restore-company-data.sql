BEGIN;

INSERT INTO public.companies
    (id, name, tax_id, address, phone, owner_id, description, website_url, status, created_at, updated_at)
SELECT
    gen_random_uuid(),
    'Creative Pulse Design Studio',
    '193456789',
    'Minsk, Belarus',
    '+375 29 555-01-20',
    u.id,
    'Студия цифровых продуктов, дизайна интерфейсов и разработки корпоративных решений.',
    'https://finflow.local',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM public.users u
WHERE u.email = 'admin@finflow.local'
  AND NOT EXISTS (
      SELECT 1 FROM public.companies c
      WHERE c.name = 'Creative Pulse Design Studio'
  );

UPDATE public.companies c
SET owner_id = u.id,
    tax_id = COALESCE(NULLIF(BTRIM(c.tax_id), ''), '193456789'),
    address = COALESCE(NULLIF(BTRIM(c.address), ''), 'Minsk, Belarus'),
    phone = COALESCE(NULLIF(BTRIM(c.phone), ''), '+375 29 555-01-20'),
    description = COALESCE(NULLIF(BTRIM(c.description), ''), 'Студия цифровых продуктов, дизайна интерфейсов и разработки корпоративных решений.'),
    website_url = COALESCE(NULLIF(BTRIM(c.website_url), ''), 'https://finflow.local'),
    status = COALESCE(NULLIF(BTRIM(c.status), ''), 'ACTIVE'),
    updated_at = CURRENT_TIMESTAMP
FROM public.users u
WHERE u.id = c.owner_id
  AND u.email = 'admin@finflow.local'
  AND c.name = 'Creative Pulse Design Studio';

INSERT INTO public.accounts
    (id, company_id, account_name, account_type, currency, balance, is_active, created_at, updated_at)
SELECT gen_random_uuid(), c.id, seed.account_name, 'BANK', seed.currency, seed.balance, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM public.companies c
JOIN public.users u ON u.id = c.owner_id
CROSS JOIN (VALUES
    ('Основной расчетный счет', 'BYN', 289000.00::numeric),
    ('Счет расчетов с иностранными клиентами', 'USD', 60500.00::numeric)
) AS seed(account_name, currency, balance)
WHERE u.email = 'admin@finflow.local'
  AND c.name = 'Creative Pulse Design Studio'
  AND NOT EXISTS (
      SELECT 1 FROM public.accounts a
      WHERE a.company_id = c.id AND a.account_name = seed.account_name
  );

INSERT INTO public.transactions
    (id, account_id, transaction_type, amount, date, description, category, counterparty, created_at)
SELECT gen_random_uuid(), a.id, sample.transaction_type, sample.amount,
       (date_trunc('month', CURRENT_DATE - (months_back.month_offset || ' months')::interval)::date + (sample.day_offset - 1)),
       sample.description, sample.category, sample.counterparty, CURRENT_TIMESTAMP
FROM public.companies c
JOIN public.users u ON u.id = c.owner_id
JOIN public.accounts a ON a.company_id = c.id AND a.account_name = 'Основной расчетный счет'
CROSS JOIN generate_series(1, 12) AS months_back(month_offset)
CROSS JOIN LATERAL (VALUES
    ('INCOME', (42000 + months_back.month_offset * 1250)::numeric, 10,
     'Поступления по договорам и проектам', 'Выручка', 'Корпоративные клиенты'),
    ('EXPENSE', (24500 + months_back.month_offset * 620)::numeric, 20,
     'Операционные расходы компании', 'Операционные расходы', 'Поставщики и подрядчики')
) AS sample(transaction_type, amount, day_offset, description, category, counterparty)
WHERE u.email = 'admin@finflow.local'
  AND c.name = 'Creative Pulse Design Studio'
  AND NOT EXISTS (
      SELECT 1 FROM public.transactions t
      JOIN public.accounts existing_account ON existing_account.id = t.account_id
      WHERE existing_account.company_id = c.id
        AND t.date >= CURRENT_DATE - INTERVAL '13 months'
  );

COMMIT;

-- Контрольный запрос: компания, владелец и связанные счета.
SELECT c.id AS company_id, c.name AS company_name, c.tax_id, c.address,
       a.id AS account_id, a.account_name, a.currency, a.balance, a.is_active
FROM public.companies c
JOIN public.users u ON u.id = c.owner_id
LEFT JOIN public.accounts a ON a.company_id = c.id
WHERE u.email = 'admin@finflow.local'
  AND c.name = 'Creative Pulse Design Studio'
ORDER BY a.account_name;
