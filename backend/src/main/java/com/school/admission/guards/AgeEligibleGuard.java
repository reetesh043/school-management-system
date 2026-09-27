package com.school.admission.guards;

import com.school.admission.domain.*;
import com.school.admission.engine.GuardResult;
import com.school.admission.engine.TransitionContext;
import com.school.admission.engine.TransitionGuard;
import org.springframework.stereotype.Component;

import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Age is measured on the academic year's cutoff date and the limits come from class_config, so a rule change
 * (a new age norm, say) is a data update rather than a deployment.
 */
@Component
class AgeEligibleGuard implements TransitionGuard {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final ClassConfigRepository classes;
    private final AcademicYearRepository years;

    AgeEligibleGuard(ClassConfigRepository classes, AcademicYearRepository years) {
        this.classes = classes;
        this.years = years;
    }

    @Override
    public String key() {
        return "AGE_ELIGIBLE";
    }

    @Override
    public String description() {
        return "Child's age fits the class on the cutoff date";
    }

    @Override
    public GuardResult check(TransitionContext ctx) {
        AdmissionApplication app = ctx.application();
        if (app.getChildDob() == null) {
            return GuardResult.fail("Add your child's date of birth before submitting.");
        }
        ClassConfig cls = classes.findById(app.getClassConfigId()).orElseThrow();
        AcademicYear year = years.findById(cls.getAcademicYearId()).orElseThrow();

        int age = Period.between(app.getChildDob(), year.getAgeCutoffDate()).getYears();
        String on = year.getAgeCutoffDate().format(DATE);

        if (cls.getMinAgeYears() != null && age < cls.getMinAgeYears()) {
            return GuardResult.fail("Your child will be " + age + " on " + on + ", and " + cls.getDisplayName()
                    + " needs " + cls.getMinAgeYears() + " or older. Choose a lower class or check the date of birth.");
        }
        if (cls.getMaxAgeYears() != null && age > cls.getMaxAgeYears()) {
            return GuardResult.fail("Your child will be " + age + " on " + on + ", and " + cls.getDisplayName()
                    + " accepts up to " + cls.getMaxAgeYears() + ". Choose a higher class or check the date of birth.");
        }
        return GuardResult.ok();
    }
}
