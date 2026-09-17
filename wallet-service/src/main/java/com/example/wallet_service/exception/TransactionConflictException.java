package com.example.wallet_service.exception;

public class TransactionConflictException extends RuntimeException {

    public TransactionConflictException(String message) {
        super(message);
    }
}