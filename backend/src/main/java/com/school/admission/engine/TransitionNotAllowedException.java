package com.school.admission.engine;

/** The caller's role may not perform this transition. Mapped to HTTP 403. */
public class TransitionNotAllowedException extends RuntimeException {

    public TransitionNotAllowedException(String message) {
        super(message);
    }
}
