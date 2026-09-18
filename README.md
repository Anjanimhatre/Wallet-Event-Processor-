# Wallet Event Processor

A Java Spring Boot backend service that processes wallet debit transactions with **idempotency and database-level concurrency control**.

This project was developed as part of the **Java Backend Intern Assignment – Idempotent Payment/Wallet Event Processor**.

## Tech Stack

* Java 21
* Spring Boot 4.1.1
* Spring Data JPA / Hibernate
* H2 in-memory database
* Maven
* JUnit 5
* MockMvc

## Core Features

### 1. Idempotent Transaction Processing

Endpoint:

```text
POST /api/v1/transactions/process
```

Example request:

```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "123e4567-e89b-12d3-a456-426614174000",
  "amount": 250.00,
  "type": "DEBIT"
}
```

The `transactionId` is unique.

If the same transaction ID is submitted concurrently, the transaction is processed only once and duplicate requests are returned as `409 Conflict`.

### 2. Concurrency Control

Wallet updates use a database-level pessimistic write lock:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

The wallet is locked inside a `@Transactional` service method before the balance is checked and updated.

This prevents concurrent debit requests from causing the wallet balance to become negative.

### 3. Insufficient Funds

If the wallet does not contain enough money for a debit, the request returns:

```text
HTTP 422 Unprocessable Entity
```

The balance is not changed and no successful transaction is stored.

## Integration Tests

The project uses an **in-memory H2 database**, so no external database configuration is required.

The test suite contains the required scenarios:

### Happy Path

> Processes a single valid debit transaction successfully.

Verifies that a valid ₹100 debit from a ₹500 wallet results in a ₹400 balance and one stored transaction.

### Idempotency

> Sends 3 identical transactionIDs simultaneously. Ensures the balance is only deducted once.

Three concurrent requests use the same transaction ID.

Expected result:

* 1 request succeeds with HTTP `200`
* 2 requests return HTTP `409`
* Final balance is ₹400
* Only 1 transaction is stored

### Race Condition

> Sends 10 concurrent debit requests of ₹100 for a wallet with a ₹500 balance. Ensures the final balance is exactly ₹0 and 5 requests fail with insufficient funds.

Expected result:

* 5 requests succeed with HTTP `200`
* 5 requests fail with HTTP `422`
* Final balance is ₹0
* 5 successful transactions are stored
* Balance never becomes negative

## Running the Tests

No external database or service is required.

From the project directory, run:

```bash
mvn clean test
```

The expected result is:

```text
Tests run: 4
Failures: 0
Errors: 0
Skipped: 0

BUILD SUCCESS
```

The tests can also be run directly from IntelliJ IDEA using the JUnit test runner.

## Database

The application uses an H2 in-memory database:

```text
jdbc:h2:mem:walletdb
```

The database is created automatically when the application/test context starts.

No MySQL, PostgreSQL, Docker, or external database setup is required.

## Project Structure

```text
wallet-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/wallet_service/
│   │   │       ├── controller/
│   │   │       ├── dto/
│   │   │       ├── entity/
│   │   │       ├── enums/
│   │   │       ├── exception/
│   │   │       ├── repository/
│   │   │       └── service/
│   │   └── resources/
│   └── test/
│       └── java/
│           └── com/example/wallet_service/
│               ├── TransactionIntegrationTest.java
│               └── WalletServiceApplicationTests.java
├── DECISIONS.md
├── README.md
└── pom.xml
```

## Error Responses

| Scenario                          |                HTTP Status |
| --------------------------------- | -------------------------: |
| Successful transaction            |                   `200 OK` |
| Duplicate/conflicting transaction |             `409 Conflict` |
| Insufficient funds                | `422 Unprocessable Entity` |
| Wallet not found                  |            `404 Not Found` |
| Invalid request                   |          `400 Bad Request` |

## Decision Log

Concurrency and implementation decisions are documented separately in:

```text
DECISIONS.md
```

This includes:

1. How the concurrency race condition was handled.
2. An example of an incorrect/sub-optimal AI-assisted suggestion and how it was corrected.

## Assignment Status

The required transaction processing, idempotency, concurrency protection, H2 integration testing, and required test scenarios have been implemented and verified with the test suite.
