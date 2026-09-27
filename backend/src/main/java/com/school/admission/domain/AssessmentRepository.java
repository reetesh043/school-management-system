package com.school.admission.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    Optional<Assessment> findFirstByApplicationIdOrderByIdDesc(Long applicationId);

    List<Assessment> findByApplicationIdOrderByIdDesc(Long applicationId);
}
