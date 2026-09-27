package com.school.admission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "payment_webhook_event")
@Getter @Setter @NoArgsConstructor
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String gateway;
    private String eventId;
    private String payload;
    private Instant receivedAt = Instant.now();
    private Instant processedAt;
    private String note;
}
