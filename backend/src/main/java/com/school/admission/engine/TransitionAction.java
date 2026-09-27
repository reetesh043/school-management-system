package com.school.admission.engine;

/**
 * A side effect that runs after every guard has passed, inside the same database transaction as the stage change.
 * Anything that talks to the outside world (WhatsApp, email) writes to the outbox instead of calling out directly,
 * so a rollback can never leave a message already sent.
 */
public interface TransitionAction {

    /** Stable key stored in stage_transition.action_keys, for example HOLD_SEAT. */
    String key();

    void execute(TransitionContext ctx);
}
