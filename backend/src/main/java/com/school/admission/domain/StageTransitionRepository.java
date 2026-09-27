package com.school.admission.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StageTransitionRepository extends JpaRepository<StageTransition, Long> {

    Optional<StageTransition> findByWorkflowIdAndFromStageIdAndToStageId(Long workflowId, Long fromStageId, Long toStageId);

    List<StageTransition> findByWorkflowIdAndFromStageId(Long workflowId, Long fromStageId);

    List<StageTransition> findByWorkflowId(Long workflowId);
}
