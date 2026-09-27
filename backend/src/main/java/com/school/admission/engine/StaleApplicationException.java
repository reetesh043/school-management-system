package com.school.admission.engine;

/** The caller acted on an out-of-date copy of the application. Mapped to HTTP 409. */
public class StaleApplicationException extends RuntimeException {

    public StaleApplicationException(long expected, long actual) {
        super("This application changed since you opened it (you had version " + expected
                + ", it is now " + actual + "). Reload and try again.");
    }
}
