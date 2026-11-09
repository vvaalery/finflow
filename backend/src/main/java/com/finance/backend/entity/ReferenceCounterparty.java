package com.finance.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;
import java.io.Serializable;

@Entity
@Table(name = "reference_counterparties")
@Data
public class ReferenceCounterparty implements Serializable {
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