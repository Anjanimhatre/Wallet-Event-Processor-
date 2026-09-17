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
import java.util.UUID;

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

        org.junit.jupiter.api.Assertions.assertEquals(
                new BigDecimal("400.00"),
                updatedWallet.getBalance()
        );

        org.junit.jupiter.api.Assertions.assertEquals(
                1,
                transactionRepository.count()
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