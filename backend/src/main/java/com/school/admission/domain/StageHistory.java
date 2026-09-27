package com.school.admission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Append-only: there is no setter and no code path that updates or deletes a row. */
@Entity
@Table(name = "stage_history")
@Getter @NoArgsConstructor
public class StageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long applicationId;

    @Column(updatable = false)
    private Long fromStageId;

    @Column(updatable = false)
    private Long toStageId;

    @Column(updatable = false)
    private String actorType;

    @Column(updatable = false)
    private String actorId;

    @Column(updatable = false)
    private String reason;

    @Column(updatable = false)
    private Instant changedAt = Instant.now();

    public static StageHistory of(Long applicationId, Long from, Long to, String actorType, String actorId, String reason) {
        StageHistory h = new StageHistory();
        h.applicationId = applicationId;
        h.fromStageId = from;
        h.toStageId = to;
        h.actorType = actorType;
        h.actorId = actorId;
        h.reason = reason;
        return h;
    }
}
