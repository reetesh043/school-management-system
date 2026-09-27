package com.school.admission.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InteractionSlotRepository extends JpaRepository<InteractionSlot, Long> {

    List<InteractionSlot> findByStartsAtAfterOrderByStartsAt(java.time.Instant from);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InteractionSlot s where s.id = :id")
    Optional<InteractionSlot> findByIdForUpdate(@Param("id") Long id);
}
