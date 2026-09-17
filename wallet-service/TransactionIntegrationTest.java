package com.example.wallet_service;

import com.example.wallet_service.entity.Wallet;
import com.example.wallet_service.enums.TransactionType;
import com.example.wallet_service.repository.TransactionRepository;
import com.example.wallet_service.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    @DisplayName("Processes a single valid debit transaction successfully.")
    void processesSingleValidDebitSuccessfully() {

        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        Wallet wallet = new Wallet(
                userId,
                new BigDecimal("500.00")
        );

        walletRepository.save(wallet);

        webTestClient
                .post()
                .uri("/api/v1/transactions/process")
                .bodyValue(
                        new TransactionRequestForTest(
                                transactionId,
                                userId,
                                new BigDecimal("100.00"),
                                TransactionType.DEBIT
                        )
                )
                .exchange()
                .expectStatus()
                .isOk();

        Wallet updatedWallet =
                walletRepository.findByUserId(userId).orElseThrow();

        System.out.println(
                "TEST: Processes a single valid debit transaction successfully."
        );

        System.out.println(
                "RESULT: Final balance = ₹" + updatedWallet.getBalance()
        );

        assertEquals(
                new BigDecimal("400.00"),
                updatedWallet.getBalance()
        );

        assertEquals(
                1,
                transactionRepository.count()
        );
    }

    @Test
    @DisplayName("Sends 3 identical transaction IDs simultaneously. Ensures the balance is only deducted once.")
    void sendsThreeIdenticalTransactionsSimultaneously() throws Exception {

        UUID userId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        Wallet wallet = new Wallet(
                userId,
                new BigDecimal("500.00")
        );

        walletRepository.save(wallet);

        String requestJson = """
                {
                    "transactionId": "%s",
                    "userId": "%s",
                    "amount": 100.00,
                    "type": "DEBIT"
                }
                """.formatted(
                transactionId,
                userId
        );

        ExecutorService executorService =
                Executors.newFixedThreadPool(3);

        Callable<Integer> request = () ->
                webTestClient
                        .post()
                        .uri("/api/v1/transactions/process")
                        .header("Content-Type", "application/json")
                        .bodyValue(requestJson)
                        .exchange()
                        .returnResult(Void.class)
                        .getStatus()
                        .value();

        List<Future<Integer>> results =
                executorService.invokeAll(
                        List.of(request, request, request)
                );

        executorService.shutdown();

        List<Integer> statuses = results.stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                })
                .toList();

        Wallet finalWallet =
                walletRepository
                        .findByUserId(userId)
                        .orElseThrow();

        long successfulRequests = statuses.stream()
                .filter(status -> status == 200)
                .count();

        long conflictRequests = statuses.stream()
                .filter(status -> status == 409)
                .count();

        System.out.println(
                "TEST: Sends 3 identical transaction IDs simultaneously."
        );

        System.out.println(
                "RESULT: HTTP statuses = " + statuses
        );

        System.out.println(
                "RESULT: Successful requests = " + successfulRequests
        );

        System.out.println(
                "RESULT: Conflict requests = " + conflictRequests
        );

        System.out.println(
                "RESULT: Final balance = ₹" + finalWallet.getBalance()
        );

        System.out.println(
                "RESULT: Transactions stored = "
                        + transactionRepository.count()
        );

        assertEquals(
                new BigDecimal("400.00"),
                finalWallet.getBalance()
        );

        assertEquals(
                1,
                transactionRepository.count()
        );

        assertEquals(
                1,
                successfulRequests
        );
    }

    record TransactionRequestForTest(
            UUID transactionId,
            UUID userId,
            BigDecimal amount,
            TransactionType type
    ) {
    }
}

