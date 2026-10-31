package com.finance.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payment_requests")
@Data
public class PaymentRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "account_id")
    private Account account;

    private String title;
    private BigDecimal amount;
    private LocalDate date;
    private String status = "PENDING"; 
    private String requester;
    private String counterparty;
    private String category;
    
    @Column(columnDefinition = "TEXT")
    private String description;
}