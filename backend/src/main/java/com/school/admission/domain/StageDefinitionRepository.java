package com.school.admission.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StageDefinitionRepository extends JpaRepository<StageDefinition, Long> {

    Optional<StageDefinition> findByWorkflowIdAndCode(Long workflowId, String code);

    List<StageDefinition> findByWorkflowIdOrderBySeqNo(Long workflowId);

    List<StageDefinition> findByCode(String code);
}
