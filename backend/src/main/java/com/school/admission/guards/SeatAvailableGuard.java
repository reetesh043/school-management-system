package com.school.admission.guards;

import com.school.admission.domain.ClassConfigRepository;
import com.school.admission.engine.GuardResult;
import com.school.admission.engine.TransitionContext;
import com.school.admission.engine.TransitionGuard;
import com.school.admission.service.SeatService;
import org.springframework.stereotype.Component;

/**
 * An early, friendly check. It is not the safety net: HOLD_SEAT re-checks under a row lock,
 * so two committee members acting at once still cannot oversell a class.
 */
@Component
class SeatAvailableGuard implements TransitionGuard {

    private final SeatService seats;
    private final ClassConfigRepository classes;

    SeatAvailableGuard(SeatService seats, ClassConfigRepository classes) {
        this.seats = seats;
        this.classes = classes;
    }

    @Override
    public String key() {
        return "SEAT_AVAILABLE";
    }

    @Override
    public String description() {
        return "A seat is available in the class";
    }

    @Override
    public GuardResult check(TransitionContext ctx) {
        Long classId = ctx.application().getClassConfigId();
        if (seats.seatsLeft(classId) > 0) {
            return GuardResult.ok();
        }
        String name = classes.findById(classId).orElseThrow().getDisplayName();
        return GuardResult.fail("No seats are left in " + name + ". You can move this application to the waitlist.");
    }
}
