package com.digitalbank.mfaservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@SpringBootTest
class MfaServiceApplicationIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReportsUp() throws Exception {
        var response = getResponse("/actuator/health");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void livenessProbeReportsUp() throws Exception {
        var response = getResponse("/actuator/health/liveness");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void readinessProbeReportsUp() throws Exception {
        var response = getResponse("/actuator/health/readiness");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void openApiDocumentPublishesServiceMetadata() throws Exception {
        var response = getResponse("/v3/api-docs");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"title\":\"Digital Bank Multi-Factor Authentication Service API\"")
                .contains("\"version\":\"1.0.0\"")
                .contains("\"/api/v1/mfa/enrollments\"")
                .contains("\"/api/v1/mfa/challenges\"");
    }

    private TestResponse getResponse(String path) throws Exception {
        var response = mockMvc.perform(get(path)).andReturn().getResponse();
        return new TestResponse(response.getStatus(), response.getContentAsString());
    }

    private record TestResponse(int status, String body) {}
}
