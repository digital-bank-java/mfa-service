package com.digitalbank.mfaservice.mfa.domain;

public record EnrollmentId(String value) {

    public EnrollmentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Enrollment id must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
