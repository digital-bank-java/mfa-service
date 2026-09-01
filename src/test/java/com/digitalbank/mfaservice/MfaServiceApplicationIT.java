package com.digitalbank.mfaservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {MfaServiceApplication.class, TestSecurityConfig.class})
class MfaServiceApplicationIT {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void healthEndpointReportsUp() throws Exception {
        var response = getResponse("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void livenessProbeReportsUp() throws Exception {
        var response = getResponse("/actuator/health/liveness");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void readinessProbeReportsUp() throws Exception {
        var response = getResponse("/actuator/health/readiness");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void openApiDocumentPublishesServiceMetadata() throws Exception {
        var response = getResponse("/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"title\":\"Digital Bank Multi-Factor Authentication Service API\"")
                .contains("\"version\":\"1.0.0\"")
                .contains("\"/api/v1/mfa/enrollments\"")
                .contains("\"/api/v1/mfa/challenges\"");
    }

    @Test
    void swaggerUiSurfaceIsDisabled() throws Exception {
        var response = getResponse("/swagger-ui/index.html");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> getResponse(String path) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
