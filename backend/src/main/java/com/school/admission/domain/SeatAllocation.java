package com.school.admission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "seat_allocation")
@Getter @Setter @NoArgsConstructor
public class SeatAllocation {

    public static final String HELD = "HELD";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String RELEASED = "RELEASED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long classConfigId;
    private Long applicationId;
    private String status;
    private Instant heldUntil;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
