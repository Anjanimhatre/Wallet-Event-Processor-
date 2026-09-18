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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionIntegrationTest {

@Autowired
private MockMvc mockMvc;

@Autowired
private WalletRepository walletRepository;

@Autowired
private TransactionRepository transactionRepository;

@BeforeEach
void cleanDatabase() {
    transactionRepository.deleteAll();
    walletRepository.deleteAll();
}

// =========================================================
// TEST 1 - SINGLE DEBIT
// =========================================================

@Test
@DisplayName("Processes a single valid debit transaction successfully.")
void processesSingleValidDebitSuccessfully() throws Exception {

    UUID userId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();

    Wallet wallet = new Wallet(
            userId,
            new BigDecimal("500.00")
    );

    walletRepository.saveAndFlush(wallet);

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

    int status = mockMvc.perform(
                    post("/api/v1/transactions/process")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson)
            )
            .andReturn()
            .getResponse()
            .getStatus();

    Wallet updatedWallet =
            walletRepository
                    .findByUserId(userId)
                    .orElseThrow();

    long transactionCount =
            transactionRepository.count();

    System.out.println("========================================");
    System.out.println("TEST: Single valid debit");
    System.out.println("RESULT: HTTP status = " + status);
    System.out.println(
            "RESULT: Final balance = ₹"
                    + updatedWallet.getBalance()
    );
    System.out.println(
            "RESULT: Transactions stored = "
                    + transactionCount
    );
    System.out.println("========================================");

    assertEquals(200, status);

    assertEquals(
            new BigDecimal("400.00"),
            updatedWallet.getBalance()
    );

    assertEquals(
            1,
            transactionCount
    );
}

// =========================================================
// TEST 2 - IDEMPOTENCY
// =========================================================

@Test
@DisplayName(
        "Sends 3 identical transaction IDs simultaneously " +
        "and deducts the wallet only once."
)
void sendsThreeIdenticalTransactionsSimultaneously()
        throws Exception {

    UUID userId = UUID.randomUUID();
    UUID transactionId = UUID.randomUUID();

    Wallet wallet = new Wallet(
            userId,
            new BigDecimal("500.00")
    );

    walletRepository.saveAndFlush(wallet);

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

    ExecutorService executor =
            Executors.newFixedThreadPool(3);

    try {

        Callable<Integer> request = () ->
                mockMvc.perform(
                                post(
                                        "/api/v1/transactions/process"
                                )
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(requestJson)
                        )
                        .andReturn()
                        .getResponse()
                        .getStatus();

        List<Callable<Integer>> requests =
                List.of(
                        request,
                        request,
                        request
                );

        List<Future<Integer>> futures =
                executor.invokeAll(requests);

        List<Integer> statuses =
                new ArrayList<>();

        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }

        Wallet finalWallet =
                walletRepository
                        .findByUserId(userId)
                        .orElseThrow();

        long successfulRequests =
                statuses.stream()
                        .filter(status -> status == 200)
                        .count();

        long conflictRequests =
                statuses.stream()
                        .filter(status -> status == 409)
                        .count();

        long transactionCount =
                transactionRepository.count();

        System.out.println("========================================");
        System.out.println(
                "TEST: 3 identical transaction IDs"
        );
        System.out.println(
                "RESULT: HTTP statuses = "
                        + statuses
        );
        System.out.println(
                "RESULT: Successful requests = "
                        + successfulRequests
        );
        System.out.println(
                "RESULT: Conflict requests = "
                        + conflictRequests
        );
        System.out.println(
                "RESULT: Final balance = ₹"
                        + finalWallet.getBalance()
        );
        System.out.println(
                "RESULT: Transactions stored = "
                        + transactionCount
        );
        System.out.println("========================================");

        assertEquals(
                new BigDecimal("400.00"),
                finalWallet.getBalance()
        );

        assertEquals(
                1,
                transactionCount
        );

        assertEquals(
                1,
                successfulRequests
        );

        assertEquals(
                2,
                conflictRequests
        );

    } finally {
        executor.shutdown();
    }
}

// =========================================================
// TEST 3 - CONCURRENT DEBITS
// =========================================================

@Test
@DisplayName(
        "Sends 10 concurrent debit requests of ₹100 against " +
        "a ₹500 wallet and prevents negative balance."
)
void sendsTenConcurrentDebitsAndPreventsNegativeBalance()
        throws Exception {

    UUID userId = UUID.randomUUID();

    Wallet wallet = new Wallet(
            userId,
            new BigDecimal("500.00")
    );

    walletRepository.saveAndFlush(wallet);

    ExecutorService executor =
            Executors.newFixedThreadPool(10);

    try {

        Callable<Integer> request = () -> {

            UUID transactionId =
                    UUID.randomUUID();

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

            return mockMvc.perform(
                            post(
                                    "/api/v1/transactions/process"
                            )
                                    .contentType(
                                            MediaType.APPLICATION_JSON
                                    )
                                    .content(requestJson)
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };

        List<Callable<Integer>> requests =
                List.of(
                        request,
                        request,
                        request,
                        request,
                        request,
                        request,
                        request,
                        request,
                        request,
                        request
                );

        List<Future<Integer>> futures =
                executor.invokeAll(requests);

        List<Integer> statuses =
                new ArrayList<>();

        for (Future<Integer> future : futures) {
            statuses.add(future.get());
        }

        Wallet finalWallet =
                walletRepository
                        .findByUserId(userId)
                        .orElseThrow();

        long successfulRequests =
                statuses.stream()
                        .filter(status -> status == 200)
                        .count();

        long insufficientFundsRequests =
                statuses.stream()
                        .filter(status -> status == 422)
                        .count();

        long transactionCount =
                transactionRepository.count();

        System.out.println("========================================");
        System.out.println(
                "TEST: 10 concurrent debit requests"
        );
        System.out.println(
                "RESULT: HTTP statuses = "
                        + statuses
        );
        System.out.println(
                "RESULT: Successful requests = "
                        + successfulRequests
        );
        System.out.println(
                "RESULT: Insufficient-funds requests = "
                        + insufficientFundsRequests
        );
        System.out.println(
                "RESULT: Final balance = ₹"
                        + finalWallet.getBalance()
        );
        System.out.println(
                "RESULT: Transactions stored = "
                        + transactionCount
        );
        System.out.println("========================================");

        assertEquals(
                new BigDecimal("0.00"),
                finalWallet.getBalance()
        );

        assertEquals(
                5,
                successfulRequests
        );

        assertEquals(
                5,
                insufficientFundsRequests
        );

        assertEquals(
                5,
                transactionCount
        );

    } finally {
        executor.shutdown();
    }
}


}