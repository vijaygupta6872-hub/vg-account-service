# Account Service

Account Service is a Spring Boot service for recording account ledger transactions in an Event Ledger system. It stores credit and debit events, enforces idempotency by `eventId`, calculates account balances, exposes recent account activity, and propagates trace IDs through logs and HTTP responses.

## Runtime

- Service name: `account-service`
- Port: `8081`
- Java: `21`
- Database: in-memory H2

## Run Locally

```powershell
.\mvnw.cmd spring-boot:run
```

The service starts at:

```text
http://localhost:8081
```

## Run Tests

```powershell
.\mvnw.cmd test
```

## API Endpoints

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/accounts/{accountId}/transactions` | Apply a credit or debit transaction |
| `GET` | `/accounts/{accountId}/balance` | Get current account balance |
| `GET` | `/accounts/{accountId}` | Get account details with recent transactions |
| `GET` | `/health` | Get service and database health |
| `GET` | `/metrics` | Get custom transaction metrics |

## Example Requests

Apply a credit:

```bash
curl -i -X POST http://localhost:8081/accounts/account-123/transactions \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: trace-123" \
  -d '{
    "eventId": "event-001",
    "type": "CREDIT",
    "amount": 100.00,
    "currency": "USD",
    "eventTimestamp": "2026-06-22T10:00:00Z"
  }'
```

Apply a debit:

```bash
curl -i -X POST http://localhost:8081/accounts/account-123/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "event-002",
    "type": "DEBIT",
    "amount": 25.00,
    "currency": "USD",
    "eventTimestamp": "2026-06-22T11:00:00Z"
  }'
```

Get balance:

```bash
curl -i http://localhost:8081/accounts/account-123/balance
```

Get account details:

```bash
curl -i http://localhost:8081/accounts/account-123
```

Check health:

```bash
curl -i http://localhost:8081/health
```

Check metrics:

```bash
curl -i http://localhost:8081/metrics
```

## Transaction Behavior

Transactions require:

- `eventId`
- `type`: `CREDIT` or `DEBIT`
- `amount`: greater than `0`
- `currency`
- `eventTimestamp`

`eventId` is unique. If the same `eventId` is received again, the service returns the existing transaction response with status `DUPLICATE` and does not create another database row.

Balance is calculated as:

```text
total CREDIT amount - total DEBIT amount
```

## Trace ID Behavior

The service reads `X-Trace-Id` from incoming requests.

- If present, the same trace ID is stored in MDC, returned in the `X-Trace-Id` response header, and included in service responses where applicable.
- If missing, the service generates a UUID trace ID, stores it in MDC, and returns it in the response header.
- Logs include the MDC `traceId` value.

## H2 Database

The service uses an in-memory H2 database:

```properties
spring.datasource.url=jdbc:h2:mem:account-service
spring.datasource.username=sa
spring.datasource.password=
```

H2 console:

```text
http://localhost:8081/h2-console
```

Connection settings:

- JDBC URL: `jdbc:h2:mem:account-service`
- User Name: `sa`
- Password: empty

The schema is recreated on startup with:

```properties
spring.jpa.hibernate.ddl-auto=create
```

## Observability

Console logs use a JSON-style format with:

- `timestamp`
- `level`
- `service`
- `traceId`
- `logger`
- `message`

The service logs transaction application, duplicate `eventId` requests, and balance lookups.

`GET /metrics` returns:

- `serviceName`
- `totalTransactionCount`
- `creditTransactionCount`
- `debitTransactionCount`
- `uniqueAccountCount`
