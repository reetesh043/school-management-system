package com.school.admission.actions;

import com.school.admission.domain.ClassConfigRepository;
import com.school.admission.engine.TransitionAction;
import com.school.admission.engine.TransitionContext;
import com.school.admission.service.SeatService;
import org.springframework.stereotype.Component;

@Component
class HoldSeatAction implements TransitionAction {

    private final SeatService seats;
    private final ClassConfigRepository classes;

    HoldSeatAction(SeatService seats, ClassConfigRepository classes) {
        this.seats = seats;
        this.classes = classes;
    }

    @Override
    public String key() {
        return "HOLD_SEAT";
    }

    @Override
    public void execute(TransitionContext ctx) {
        Long classId = ctx.application().getClassConfigId();
        int holdDays = classes.findById(classId).orElseThrow().getFeeDueDays();
        seats.hold(ctx.application().getId(), classId, holdDays);
    }
}
