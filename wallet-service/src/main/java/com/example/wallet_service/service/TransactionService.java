```java
package com.example.wallet_service.service;

import com.example.wallet_service.dto.TransactionRequest;
import com.example.wallet_service.dto.TransactionResponse;
import com.example.wallet_service.entity.Transaction;
import com.example.wallet_service.entity.Wallet;
import com.example.wallet_service.enums.TransactionStatus;
import com.example.wallet_service.enums.TransactionType;
import com.example.wallet_service.exception.InsufficientFundsException;
import com.example.wallet_service.exception.TransactionConflictException;
import com.example.wallet_service.exception.WalletNotFoundException;
import com.example.wallet_service.repository.TransactionRepository;
import com.example.wallet_service.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class TransactionService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository
    ) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionResponse process(TransactionRequest request) {

        /*
         * Step 1:
         * Check whether this transaction was already processed.
         */
        Optional<Transaction> existingTransaction =
                transactionRepository.findByTransactionId(
                        request.getTransactionId()
                );

        if (existingTransaction.isPresent()) {

            Transaction existing = existingTransaction.get();

            boolean sameRequest =
                    existing.getUserId().equals(request.getUserId())
                            && existing.getAmount().compareTo(request.getAmount()) == 0
                            && existing.getType() == request.getType();

            if (!sameRequest) {
                throw new TransactionConflictException(
                        "Transaction ID already exists with different transaction details"
                );
            }

            return toResponse(
                    existing,
                    getCurrentBalance(request)
            );
        }

        /*
         * Step 2:
         * Lock the wallet at database level.
         */
        Wallet wallet = walletRepository
                .findByUserId(request.getUserId())
                .orElseThrow(() ->
                        new WalletNotFoundException(
                                "Wallet not found for user: "
                                        + request.getUserId()
                        )
                );

        /*
         * Step 3:
         * Currently only DEBIT is supported.
         */
        if (request.getType() != TransactionType.DEBIT) {
            throw new IllegalArgumentException(
                    "Only DEBIT transactions are currently supported"
            );
        }

        /*
         * Step 4:
         * Check whether the wallet has enough money.
         */
        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {

            throw new InsufficientFundsException(
                    "Insufficient funds. Available balance: "
                            + wallet.getBalance()
            );
        }

        /*
         * Step 5:
         * Deduct the amount.
         */
        BigDecimal newBalance =
                wallet.getBalance().subtract(request.getAmount());

        wallet.setBalance(newBalance);

        walletRepository.save(wallet);

        /*
         * Step 6:
         * Store the successful transaction.
         */
        Transaction transaction = new Transaction(
                request.getTransactionId(),
                request.getUserId(),
                request.getAmount(),
                request.getType(),
                TransactionStatus.SUCCESS
        );

        transactionRepository.save(transaction);

        /*
         * Step 7:
         * Return the result.
         */
        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getUserId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getStatus(),
                wallet.getBalance(),
                "Transaction processed successfully"
        );
    }

    private BigDecimal getCurrentBalance(TransactionRequest request) {
        return walletRepository
                .findByUserId(request.getUserId())
                .map(Wallet::getBalance)
                .orElse(null);
    }

    private TransactionResponse toResponse(
            Transaction transaction,
            BigDecimal balance
    ) {
        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getUserId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getStatus(),
                balance,
                "Transaction was already processed"
        );
    }
}
```
