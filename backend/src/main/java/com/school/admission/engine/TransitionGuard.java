package com.school.admission.engine;

/**
 * A read-only precondition. Guards never change data; they only decide.
 * Add one by declaring a Spring bean, then reference its key from stage_transition.guard_keys.
 */
public interface TransitionGuard {

    /** Stable key stored in stage_transition.guard_keys, for example DOCS_VERIFIED. */
    String key();

    /** Short description shown next to the guard in admin and console screens. */
    String description();

    GuardResult check(TransitionContext ctx);
}
