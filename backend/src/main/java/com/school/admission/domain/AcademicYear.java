package com.school.admission.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "academic_year")
@Getter @Setter @NoArgsConstructor
public class AcademicYear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label;
    private LocalDate startsOn;
    private LocalDate endsOn;
    /** A child's age is measured on this date. */
    private LocalDate ageCutoffDate;
    private boolean admissionsOpen;
}
