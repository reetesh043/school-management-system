package com.school.admission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "class_config")
@Getter @Setter @NoArgsConstructor
public class ClassConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long academicYearId;
    private String classCode;
    private String displayName;
    private Integer minAgeYears;
    private Integer maxAgeYears;
    private int seatsTotal;
    /** Seats already taken by continuing students. */
    private int seatsPreFilled;
    private BigDecimal passMark;
    private BigDecimal admissionFee;
    private int feeDueDays;
    private Long workflowId;
}
