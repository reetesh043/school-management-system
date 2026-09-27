package com.school.admission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "application_document")
@Getter @Setter @NoArgsConstructor
public class ApplicationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long applicationId;
    private String docType;
    private String fileName;
    private String contentType;
    private long sizeBytes;
    private String storageKey;
    private String verificationStatus = "UPLOADED";
    private String rejectReason;
    private Long verifiedById;
    private Instant verifiedAt;
    private Instant uploadedAt = Instant.now();
}
