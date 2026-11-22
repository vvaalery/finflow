package com.finance.backend.repository;

import com.finance.backend.entity.PaymentRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, UUID> {
    List<PaymentRequest> findAllByOrderByDateDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentRequest r where r.id = :id")
    Optional<PaymentRequest> findForUpdate(@Param("id") UUID id);
}