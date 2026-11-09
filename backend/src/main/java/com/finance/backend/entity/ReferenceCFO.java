package com.finance.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;
import java.io.Serializable;

@Entity
@Table(name = "reference_cfo")
@Data
public class ReferenceCFO implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private String name;
    
    private String description;
}