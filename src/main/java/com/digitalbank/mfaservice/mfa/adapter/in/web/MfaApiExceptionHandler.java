package com.digitalbank.mfaservice.mfa.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class MfaApiExceptionHandler {

    @ExceptionHandler(MfaProblemException.class)
    ResponseEntity<ProblemDetail> handleMfaProblem(MfaProblemException exception, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle(exception.title());
        problem.setType(exception.type());
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        exception.properties().forEach(problem::setProperty);
        return ResponseEntity.status(exception.status()).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidationFailure(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .toList();

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setType(java.net.URI.create("https://digital-bank-java.local/problems/validation-error"));
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        var errors = exception.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", violation.getPropertyPath().toString(),
                        "message", violation.getMessage()))
                .toList();

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setType(java.net.URI.create("https://digital-bank-java.local/problems/validation-error"));
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException exception, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setType(java.net.URI.create("https://digital-bank-java.local/problems/validation-error"));
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        problem.setProperty("errors", java.util.List.of(Map.of("field", "request", "message", exception.getMessage())));
        return ResponseEntity.badRequest().body(problem);
    }
}
