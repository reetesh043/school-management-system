package com.school.admission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "interaction_slot")
@Getter @Setter @NoArgsConstructor
public class InteractionSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String kind;
    private Instant startsAt;
    private Instant endsAt;
    private int capacity;
    private int booked;
}
