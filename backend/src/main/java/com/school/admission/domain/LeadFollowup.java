package com.school.admission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "lead_followup")
@Getter @Setter @NoArgsConstructor
public class LeadFollowup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long leadId;
    private Instant dueAt;
    private Instant doneAt;
    private String note;
    private Long createdById;
}
