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
     * Only DEBIT is currently supported.
     */
    if (request.getType() != TransactionType.DEBIT) {
        throw new IllegalArgumentException(
                "Only DEBIT transactions are currently supported"
        );
    }

    /*
     * IMPORTANT:
     *
     * Acquire a database-level PESSIMISTIC_WRITE lock on
     * the wallet BEFORE checking idempotency.
     *
     * Concurrent transactions for the same wallet are therefore
     * serialized.
     */
    Wallet wallet = walletRepository
            .findByUserIdForUpdate(request.getUserId())
            .orElseThrow(() ->
                    new WalletNotFoundException(
                            "Wallet not found for user: "
                                    + request.getUserId()
                    )
            );

    /*
     * Check idempotency AFTER acquiring the wallet lock.
     *
     * The first request creates the transaction.
     *
     * Any later request with the same transaction ID reaches
     * this point only after the first request has committed.
     */
    Optional<Transaction> existingTransaction =
            transactionRepository.findByTransactionId(
                    request.getTransactionId()
            );

    if (existingTransaction.isPresent()) {

        Transaction existing = existingTransaction.get();

        boolean sameRequest =
                existing.getUserId().equals(request.getUserId())
                        && existing.getAmount()
                                .compareTo(request.getAmount()) == 0
                        && existing.getType() == request.getType();

        if (!sameRequest) {
            throw new TransactionConflictException(
                    "Transaction ID already exists with different transaction details"
            );
        }

        /*
         * Your integration test explicitly expects duplicate
         * requests to return HTTP 409.
         */
        throw new TransactionConflictException(
                "Transaction was already processed"
        );
    }

    /*
     * Check balance while the wallet is locked.
     */
    if (wallet.getBalance().compareTo(request.getAmount()) < 0) {

        throw new InsufficientFundsException(
                "Insufficient funds. Available balance: "
                        + wallet.getBalance()
        );
    }

    /*
     * Deduct money.
     */
    BigDecimal newBalance =
            wallet.getBalance()
                    .subtract(request.getAmount());

    wallet.setBalance(newBalance);

    walletRepository.save(wallet);

    /*
     * Create transaction record.
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
     * Return successful response.
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


}
