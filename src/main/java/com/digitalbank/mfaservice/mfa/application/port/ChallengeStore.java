package com.digitalbank.mfaservice.mfa.application.port;

import com.digitalbank.mfaservice.mfa.domain.Challenge;
import com.digitalbank.mfaservice.mfa.domain.ChallengeId;
import java.util.Optional;

public interface ChallengeStore {

    void save(Challenge challenge);

    Optional<Challenge> find(ChallengeId challengeId);

    default Optional<Challenge> findByDecisionId(String decisionId) {
        return Optional.empty();
    }
}
