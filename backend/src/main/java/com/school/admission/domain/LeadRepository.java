package com.school.admission.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {

    Optional<Lead> findFirstByPhoneAndChildDob(String phone, java.time.LocalDate childDob);

    long countByAssignedToIdAndStatusNotIn(Long assignedToId, java.util.Collection<String> statuses);

    List<Lead> findByAssignedToId(Long assignedToId);
}
