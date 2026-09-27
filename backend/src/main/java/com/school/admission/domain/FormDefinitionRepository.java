package com.school.admission.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormDefinitionRepository extends JpaRepository<FormDefinition, Long> {

    Optional<FormDefinition> findFirstByPurposeAndStatusOrderByVersionDesc(String purpose, String status);

    List<FormDefinition> findByPurposeAndStatus(String purpose, String status);

    List<FormDefinition> findByNameOrderByVersionDesc(String name);
}
