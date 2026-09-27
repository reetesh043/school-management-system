package com.school.admission.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {

    boolean existsByGatewayAndEventId(String gateway, String eventId);

    Optional<PaymentWebhookEvent> findByGatewayAndEventId(String gateway, String eventId);
}
