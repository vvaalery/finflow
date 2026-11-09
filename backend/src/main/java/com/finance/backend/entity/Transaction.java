package com.finance.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "transaction_type", nullable = false)
    private String transactionType;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "description")
    private String description;

    @Column(name = "category")
    private String category;

    @Column(name = "counterparty")
    private String counterparty;

    @Column(name = "cfo_id")
    private UUID cfoId;

    @ManyToOne
    @JoinColumn(name = "cash_flow_item_id")
    private ReferenceCashFlowItem cashFlowItem;

    @ManyToOne
    @JoinColumn(name = "counterparty_id")
    private ReferenceCounterparty counterpartyRef;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}