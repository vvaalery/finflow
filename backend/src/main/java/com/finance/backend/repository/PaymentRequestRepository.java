package com.finance.backend.repository;

import com.finance.backend.entity.PaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, UUID> {
    List<PaymentRequest> findAllByOrderByDateDesc();
}