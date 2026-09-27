package com.school.admission.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClassConfigRepository extends JpaRepository<ClassConfig, Long> {

    Optional<ClassConfig> findByAcademicYearIdAndClassCode(Long academicYearId, String classCode);

    List<ClassConfig> findByAcademicYearIdOrderById(Long academicYearId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClassConfig c where c.id = :id")
    Optional<ClassConfig> findByIdForUpdate(@Param("id") Long id);
}
