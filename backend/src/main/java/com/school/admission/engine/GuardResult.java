package com.school.admission.engine;

/** Outcome of one guard. The message is written for the person who will read it, not for developers. */
public record GuardResult(boolean passed, String message) {

    public static GuardResult ok() {
        return new GuardResult(true, null);
    }

    public static GuardResult fail(String message) {
        return new GuardResult(false, message);
    }
}
