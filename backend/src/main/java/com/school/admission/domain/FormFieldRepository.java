package com.school.admission.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormFieldRepository extends JpaRepository<FormField, Long> {

    List<FormField> findByFormDefinitionIdOrderBySeqNo(Long formDefinitionId);
}
