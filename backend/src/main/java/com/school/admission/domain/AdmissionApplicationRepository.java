package com.school.admission.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdmissionApplicationRepository extends JpaRepository<AdmissionApplication, Long>, JpaSpecificationExecutor<AdmissionApplication> {

    Optional<AdmissionApplication> findFirstByLeadId(Long leadId);

    List<AdmissionApplication> findByAssignedToId(Long assignedToId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AdmissionApplication a where a.id = :id")
    Optional<AdmissionApplication> findByIdForUpdate(@Param("id") Long id);
}
