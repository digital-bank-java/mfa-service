package com.digitalbank.mfaservice.mfa.adapter.memory;

import com.digitalbank.mfaservice.mfa.application.port.ChallengeStore;
import com.digitalbank.mfaservice.mfa.domain.Challenge;
import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryChallengeStore implements ChallengeStore {

    private final ConcurrentHashMap<ChallengeId, Challenge> challenges = new ConcurrentHashMap<>();

    @Override
    public void save(Challenge challenge) {
        var previous = challenges.putIfAbsent(challenge.id(), challenge);
        if (previous != null && previous != challenge) {
            throw new IllegalStateException("Challenge id already exists");
        }
    }

    @Override
    public Optional<Challenge> find(ChallengeId challengeId) {
        return Optional.ofNullable(challenges.get(challengeId));
    }
}
