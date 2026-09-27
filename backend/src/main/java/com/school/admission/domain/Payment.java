package com.school.admission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment")
@Getter @Setter @NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long invoiceId;
    private String gateway;
    private String gatewayOrderId;
    private String gatewayPaymentId;
    private String idempotencyKey;
    private BigDecimal amount;
    private String status = "INITIATED";
    private String method;
    private Instant settledAt;
    private Instant createdAt = Instant.now();
}
