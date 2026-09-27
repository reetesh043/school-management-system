package com.school.admission.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {

    Optional<AcademicYear> findFirstByAdmissionsOpenTrueOrderByIdDesc();

    Optional<AcademicYear> findByLabel(String label);
}
