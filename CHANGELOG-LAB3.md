# Реализованные изменения

- Dockerfile backend/frontend и docker-compose для PostgreSQL, backend, frontend и ежедневных backup.
- HTTPS/TLS для backend через PKCS12-сертификат, создаваемый автоматически.
- JWT Bearer authentication и BCrypt 12 rounds.
- Ролевая модель ADMIN / DIRECTOR / FINANCIAL_MANAGER.
- GraphQL Query + Mutation, input validation, фильтрация, сортировка, агрегация.
- Mock gateway между GraphQL и внутренним финансовым сервисом.
- Mock CompanyService gateway для будущего PaymentService -> CompanyService gRPC.
- company.proto как контракт будущего gRPC-взаимодействия.
- Excel и CSV import для транзакций и платежей.
- Excel export отчётов.
- JSONB audit log с action/entity/old/new/IP/User-Agent.
- PostgreSQL индексы по внешним ключам и аналитическим полям.
- Транзакционные операции изменения баланса и финансовых сущностей.
- Три сценария cash-flow: BASE / OPTIMISTIC / PESSIMISTIC.
- Алгоритмы Moving Average, Linear Regression и Gradient Boosting с сохранением параметров модели.
- Сохранение результатов прогноза и помесячных ForecastItem.
- Frontend token-based Authorization вместо хранения пароля для API-запросов.
- Скрипты запуска, backup, restore и benchmark.
