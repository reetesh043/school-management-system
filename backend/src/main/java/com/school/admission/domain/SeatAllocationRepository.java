package com.school.admission.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeatAllocationRepository extends JpaRepository<SeatAllocation, Long> {

    Optional<SeatAllocation> findByApplicationId(Long applicationId);

    long countByClassConfigIdAndStatusIn(Long classConfigId, java.util.Collection<String> statuses);
}
