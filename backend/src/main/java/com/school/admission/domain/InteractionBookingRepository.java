package com.school.admission.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InteractionBookingRepository extends JpaRepository<InteractionBooking, Long> {

    List<InteractionBooking> findByApplicationId(Long applicationId);
}
