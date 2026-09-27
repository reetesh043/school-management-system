package com.school.admission.guards;

import com.school.admission.domain.Assessment;
import com.school.admission.domain.AssessmentRepository;
import com.school.admission.domain.ClassConfig;
import com.school.admission.domain.ClassConfigRepository;
import com.school.admission.engine.GuardResult;
import com.school.admission.engine.TransitionContext;
import com.school.admission.engine.TransitionGuard;
import org.springframework.stereotype.Component;

/** The latest assessment must exist and be at or above the class pass mark. */
@Component
class AssessmentPassedGuard implements TransitionGuard {

    private final AssessmentRepository assessments;
    private final ClassConfigRepository classes;

    AssessmentPassedGuard(AssessmentRepository assessments, ClassConfigRepository classes) {
        this.assessments = assessments;
        this.classes = classes;
    }

    @Override
    public String key() {
        return "ASSESSMENT_PASSED";
    }

    @Override
    public String description() {
        return "Interaction score is recorded and meets the pass mark";
    }

    @Override
    public GuardResult check(TransitionContext ctx) {
        Assessment latest = assessments.findFirstByApplicationIdOrderByIdDesc(ctx.application().getId()).orElse(null);
        if (latest == null) {
            return GuardResult.fail("The interaction score has not been recorded yet.");
        }
        if (!latest.isPassed()) {
            ClassConfig cls = classes.findById(ctx.application().getClassConfigId()).orElseThrow();
            return GuardResult.fail("The score of " + latest.getScore().stripTrailingZeros().toPlainString()
                    + " is below the pass mark of " + cls.getPassMark().stripTrailingZeros().toPlainString() + ".");
        }
        return GuardResult.ok();
    }
}
