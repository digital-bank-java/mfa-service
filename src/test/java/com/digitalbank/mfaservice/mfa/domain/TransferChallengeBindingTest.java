package com.digitalbank.mfaservice.mfa.domain;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TransferChallengeBindingTest {

    private static final String SOURCE_ACCOUNT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String DESTINATION_ACCOUNT_ID = "22222222-2222-2222-2222-222222222222";
    private static final String DECISION_ID = "44444444-4444-4444-4444-444444444444";

    @Test
    void rejectsIdentifiersThatWouldViolateTheMfaAssuranceContract() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> binding("decision-1", SOURCE_ACCOUNT_ID, DESTINATION_ACCOUNT_ID))
                .withMessage("decisionId must be a UUID");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> binding(DECISION_ID, "account-source", DESTINATION_ACCOUNT_ID))
                .withMessage("sourceAccountId must be a UUID");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> binding(DECISION_ID, SOURCE_ACCOUNT_ID, "account-destination"))
                .withMessage("destinationAccountId must be a UUID");
    }

    private static TransferChallengeBinding binding(
            String decisionId, String sourceAccountId, String destinationAccountId) {
        return new TransferChallengeBinding(
                "transfer-1",
                "reservation-1",
                decisionId,
                "decision-request-1",
                "customer-1",
                sourceAccountId,
                destinationAccountId,
                new BigDecimal("100.00"),
                "USD",
                "policy-1",
                "correlation-1");
    }
}
