package com.digitalbank.mfaservice.mfa.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class MfaApiExceptionHandlerTest {

    private final MfaApiExceptionHandler handler = new MfaApiExceptionHandler();

    @Test
    void sanitizesIllegalArgumentExceptionDetails() {
        var request = new MockHttpServletRequest("POST", "/api/v1/mfa/enrollments");

        var response = handler.handleIllegalArgument(
                new IllegalArgumentException("Enrollment id must not be blank: TEST-SECRET"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Request validation failed");
        assertThat(String.valueOf(response.getBody().getProperties().get("errors")))
                .doesNotContain("TEST-SECRET");
    }
}
