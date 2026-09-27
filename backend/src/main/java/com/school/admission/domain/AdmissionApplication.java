package com.school.admission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One child's admission. Stage ids are plain foreign keys rather than associations, so moving a stage never
 * loads more than it needs. {@code version} gives optimistic locking to API callers, and the engine also
 * takes a pessimistic row lock while a transition runs.
 */
@Entity
@Table(name = "application")
@Getter @Setter @NoArgsConstructor
public class AdmissionApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String applicationNo;
    private Long academicYearId;
    private Long classConfigId;
    private Long workflowId;
    private Long stageId;
    private Long prevStageId;
    private Long leadId;
    private Long guardianUserId;
    private Long assignedToId;

    private String childName;
    private LocalDate childDob;
    private String gender;
    private String previousSchool;
    private String guardianName;
    private String guardianPhone;
    private String guardianEmail;

    private Long formDefinitionId;
    private Integer formVersion;

    /** Answers to the configurable form fields, stored as a JSON object. */
    private String responses;

    private Instant submittedAt;
    private String admissionNo;

    @Version
    private Long version;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /** Moves to another stage. Side stages (waitlist, reject) remember where the application came from. */
    public void moveTo(Long newStageId, boolean rememberPrevious) {
        if (rememberPrevious) {
            this.prevStageId = this.stageId;
        }
        this.stageId = newStageId;
    }
}
