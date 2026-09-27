package com.school.admission.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StageHistoryRepository extends JpaRepository<StageHistory, Long> {

    List<StageHistory> findByApplicationIdOrderByIdAsc(Long applicationId);

    @Query("select h.toStageId, count(distinct h.applicationId) from StageHistory h group by h.toStageId")
    List<Object[]> countApplicationsReachedPerStage();
}
