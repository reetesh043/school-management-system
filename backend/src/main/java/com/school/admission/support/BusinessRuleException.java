package com.school.admission.support;

/** A request was understood but breaks a business rule (admissions closed, form locked...). Mapped to HTTP 422. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
