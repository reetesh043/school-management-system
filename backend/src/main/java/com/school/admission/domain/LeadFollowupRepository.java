package com.school.admission.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeadFollowupRepository extends JpaRepository<LeadFollowup, Long> {

    List<LeadFollowup> findByLeadIdOrderByDueAt(Long leadId);
}
