package com.school.admission.service;

import com.school.admission.domain.OutboxEvent;
import com.school.admission.domain.OutboxEventRepository;
import com.school.admission.support.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Polls the outbox and renders each pending event's notification. In production this becomes a queue consumer
 * (SQS/SNS); polling keeps the demo dependency-free. Each event is marked published even if rendering fails,
 * so a bad template cannot jam the queue; the failure itself is recorded in communication_log.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outbox;
    private final CommunicationService communication;
    private final Json json;

    public OutboxRelay(OutboxEventRepository outbox, CommunicationService communication, Json json) {
        this.outbox = outbox;
        this.communication = communication;
        this.json = json;
    }

    @Scheduled(fixedDelayString = "${admission.outbox.poll-interval-ms:3000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outbox.findTop50ByPublishedAtIsNullOrderByIdAsc();
        for (OutboxEvent event : pending) {
            try {
                if ("application.stage_changed".equals(event.getEventType())) {
                    Map<String, Object> payload = json.toMap(event.getPayload());
                    Long applicationId = ((Number) payload.get("applicationId")).longValue();
                    communication.sendStageMessage(applicationId, (String) payload.get("templateCode"));
                }
            } catch (Exception e) {
                log.warn("Could not relay outbox event {}: {}", event.getId(), e.getMessage());
            } finally {
                event.setPublishedAt(Instant.now());
                event.setAttempts(event.getAttempts() + 1);
            }
        }
    }
}
