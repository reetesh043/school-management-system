package com.school.admission.actions;

import com.school.admission.domain.OutboxEvent;
import com.school.admission.domain.OutboxEventRepository;
import com.school.admission.engine.TransitionAction;
import com.school.admission.engine.TransitionContext;
import com.school.admission.support.Json;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Transactional outbox: the notification is a row committed together with the stage change.
 * OutboxRelay renders the STAGE_<STAGE> templates afterwards. If the transition rolls back, no message exists,
 * so a parent never receives "you are admitted" for a change that did not happen.
 */
@Component
class NotifyAction implements TransitionAction {

    private final OutboxEventRepository outbox;
    private final Json json;

    NotifyAction(OutboxEventRepository outbox, Json json) {
        this.outbox = outbox;
        this.json = json;
    }

    @Override
    public String key() {
        return "NOTIFY";
    }

    @Override
    public void execute(TransitionContext ctx) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("application");
        event.setAggregateId(ctx.application().getId());
        event.setEventType("application.stage_changed");
        event.setPayload(json.write(Map.of(
                "applicationId", ctx.application().getId(),
                "fromStage", ctx.from().getCode(),
                "toStage", ctx.to().getCode(),
                "templateCode", "STAGE_" + ctx.to().getCode())));
        outbox.save(event);
    }
}
