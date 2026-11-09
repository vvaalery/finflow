package com.finance.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;
import java.io.Serializable;

@Entity
@Table(name = "reference_cash_flow_items")
@Data
public class ReferenceCashFlowItem implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private String type;
    
    private String description;
}