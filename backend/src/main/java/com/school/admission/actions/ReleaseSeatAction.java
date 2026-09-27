package com.school.admission.actions;

import com.school.admission.engine.TransitionAction;
import com.school.admission.engine.TransitionContext;
import com.school.admission.service.SeatService;
import org.springframework.stereotype.Component;

/** Gives a held seat back when an application in PAYMENT is waitlisted or rejected. */
@Component
class ReleaseSeatAction implements TransitionAction {

    private final SeatService seats;

    ReleaseSeatAction(SeatService seats) {
        this.seats = seats;
    }

    @Override
    public String key() {
        return "RELEASE_SEAT";
    }

    @Override
    public void execute(TransitionContext ctx) {
        seats.release(ctx.application().getId());
    }
}
