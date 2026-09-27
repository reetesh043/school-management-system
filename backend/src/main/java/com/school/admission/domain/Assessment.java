package com.school.admission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "assessment")
@Getter @Setter @NoArgsConstructor
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long applicationId;
    private String kind;
    private BigDecimal score;
    private boolean passed;
    private String remarks;
    private Long evaluatedById;
    private Instant evaluatedAt = Instant.now();
}
