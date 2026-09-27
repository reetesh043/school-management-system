package com.school.admission.web;

import com.school.admission.engine.StaleApplicationException;
import com.school.admission.engine.TransitionNotAllowedException;
import com.school.admission.engine.TransitionRejectedException;
import com.school.admission.support.BusinessRuleException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.NoSuchElementException;

/** Maps every exception the API can throw to an RFC 9457 problem response a UI can show as-is. */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(TransitionRejectedException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    ProblemDetail rejected(TransitionRejectedException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,
                "This move is blocked until the items below are fixed.");
        p.setTitle("Transition rejected");
        p.setProperty("failures", e.getFailures());
        return p;
    }

    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    ProblemDetail businessRule(BusinessRuleException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        p.setTitle("Not possible right now");
        return p;
    }

    @ExceptionHandler({TransitionNotAllowedException.class, AccessDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ProblemDetail forbidden(RuntimeException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                e instanceof TransitionNotAllowedException ? e.getMessage() : "You do not have permission to do this.");
        p.setTitle("Not allowed");
        return p;
    }

    @ExceptionHandler({StaleApplicationException.class, OptimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    ProblemDetail conflict(RuntimeException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                e instanceof StaleApplicationException
                        ? e.getMessage()
                        : "Someone else updated this application. Reload and try again.");
        p.setTitle("Application changed");
        return p;
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ProblemDetail badRequest(Exception e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        p.setTitle("Invalid request");
        return p;
    }
}
