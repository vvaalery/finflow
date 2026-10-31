package com.finance.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;

@Entity
@Table(name = "reference_counterparties")
@Data
public class ReferenceCounterparty {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String name;
    
    private String taxId;
    private String address;
    private String phone;
    private String email;
}