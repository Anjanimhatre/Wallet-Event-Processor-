# Decision Log

## 1. How did you handle the concurrency race condition?

The wallet balance update is protected using a **database-level pessimistic write lock**.

The `WalletRepository` contains a dedicated method:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        SELECT w
        FROM Wallet w
        WHERE w.userId = :userId
        """)
Optional<Wallet> findByUserIdForUpdate(
        @Param("userId") UUID userId
);
```

`TransactionService` calls `findByUserIdForUpdate()` inside a method annotated with `@Transactional`. This ensures that concurrent transactions attempting to modify the same wallet cannot simultaneously read and update the same balance. The transactions are serialized at the database level.

The balance is checked only **after** the wallet lock has been acquired:

```java
if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
    throw new InsufficientFundsException(
            "Insufficient funds. Available balance: "
                    + wallet.getBalance()
    );
}
```

The amount is then deducted and the transaction is persisted within the same database transaction.

This prevents the race condition where multiple requests could otherwise read the same balance and cause the wallet balance to become negative.

---

## 2. Where did your AI assistant give you an incorrect or sub-optimal suggestion?

### Idempotency check ordering

An early implementation suggestion checked transaction idempotency **before** acquiring the wallet database lock.

That approach was sub-optimal for the concurrency requirement because multiple concurrent requests with the same transaction ID could pass the initial lookup before one of them had committed the transaction. This could result in multiple requests attempting to create the same transaction concurrently.

**Fix:** The implementation was changed so that the wallet is locked first using `PESSIMISTIC_WRITE`, and the transaction ID is checked **after** the lock has been acquired.

### Incorrect Spring Boot test import

Another issue occurred while configuring the integration test. An initial test version used an incorrect Spring Boot test import:

```java
org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
```

For the Spring Boot version used by this project, the correct import is:

```java
org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
```

The test was updated accordingly.

### Verification

The final implementation was verified with the complete test suite, including:

- Concurrent idempotency tests
- Concurrent balance-debit tests
- The complete integration test suite
