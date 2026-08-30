package com.digitalbank.mfaservice.mfa.domain;

public record ChallengeId(String value) {

    public ChallengeId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Challenge id must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
