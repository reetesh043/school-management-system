package com.school.admission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "lead")
@Getter @Setter @NoArgsConstructor
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String guardianName;
    private String phone;
    private String email;
    private String childName;
    private LocalDate childDob;
    private String classCode;
    private String source;
    private String status = "NEW";
    private String lostReason;
    private boolean consent;
    private Long assignedToId;
    private Instant nextFollowUpAt;
    private Instant createdAt = Instant.now();
}
