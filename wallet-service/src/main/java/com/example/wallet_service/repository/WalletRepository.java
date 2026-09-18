package com.example.wallet_service.repository;

import com.example.wallet_service.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, Long> {


Optional<Wallet> findByUserId(UUID userId);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        SELECT w
        FROM Wallet w
        WHERE w.userId = :userId
        """)
Optional<Wallet> findByUserIdForUpdate(
        @Param("userId") UUID userId
);


}
