package com.kilor.demo.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "token_holder_snapshot", indexes = {
    @Index(name = "idx_contract_date", columnList = "contractAddress, snapshotDate")
})
public class TokenHolderSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String contractAddress;

    @Column(nullable = false)
    private String holderAddress;

    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private String balance;

    private Double percentage;

    @Column(nullable = false)
    private LocalDate snapshotDate;
}
