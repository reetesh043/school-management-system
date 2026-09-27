package com.school.admission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "invoice")
@Getter @Setter @NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String invoiceNo;
    private Long applicationId;
    private String description;
    private BigDecimal amount;
    private String currency = "INR";
    private String status = "ISSUED";
    private Instant issuedAt = Instant.now();
    private Instant dueAt;
    private Instant paidAt;
}
