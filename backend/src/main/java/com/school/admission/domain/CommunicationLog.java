package com.school.admission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "communication_log")
@Getter @Setter @NoArgsConstructor
public class CommunicationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long applicationId;
    private String channel;
    private String templateCode;
    private String toAddress;
    private String message;
    private String status;
    private String error;
    private Instant createdAt = Instant.now();
}
