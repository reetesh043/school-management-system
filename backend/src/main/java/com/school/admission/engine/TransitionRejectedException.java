package com.school.admission.engine;

import java.util.List;

/** The transition does not exist or one or more guards failed. Mapped to HTTP 422 with every failure listed. */
public class TransitionRejectedException extends RuntimeException {

    private final List<String> failures;

    public TransitionRejectedException(List<String> failures) {
        super(String.join(" ", failures));
        this.failures = List.copyOf(failures);
    }

    public List<String> getFailures() {
        return failures;
    }
}
