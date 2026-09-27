package com.school.admission.actions;

import com.school.admission.domain.AcademicYear;
import com.school.admission.domain.AcademicYearRepository;
import com.school.admission.engine.TransitionAction;
import com.school.admission.engine.TransitionContext;
import com.school.admission.service.SeatService;
import org.springframework.stereotype.Component;

/** Turns the held seat into a confirmed one and assigns the admission number. */
@Component
class ConfirmAdmissionAction implements TransitionAction {

    private final SeatService seats;
    private final AcademicYearRepository years;

    ConfirmAdmissionAction(SeatService seats, AcademicYearRepository years) {
        this.seats = seats;
        this.years = years;
    }

    @Override
    public String key() {
        return "CONFIRM_ADMISSION";
    }

    @Override
    public void execute(TransitionContext ctx) {
        seats.confirm(ctx.application().getId());
        AcademicYear year = years.findById(ctx.application().getAcademicYearId()).orElseThrow();
        // The application id is unique, so the admission number can never collide.
        ctx.application().setAdmissionNo(String.format("ADM%02d-%05d",
                year.getStartsOn().getYear() % 100, ctx.application().getId()));
    }
}
