package com.school.admission.engine;

import com.school.admission.domain.AdmissionApplication;
import com.school.admission.domain.StageDefinition;
import com.school.admission.domain.StageTransition;

/** Everything a guard or action may need to know about the transition in progress. */
public record TransitionContext(
        AdmissionApplication application,
        StageTransition transition,
        StageDefinition from,
        StageDefinition to,
        Actor actor,
        String reason) {
}
