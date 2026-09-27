package com.school.admission.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByApplicationIdOrderByIdDesc(Long applicationId);

    boolean existsByApplicationIdAndStatus(Long applicationId, String status);

    Optional<Invoice> findFirstByApplicationIdAndStatus(Long applicationId, String status);
}
