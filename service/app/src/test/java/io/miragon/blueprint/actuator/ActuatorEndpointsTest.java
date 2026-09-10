package io.miragon.blueprint.actuator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke-tests the operational surface added for issue #10: the actuator probes and the Prometheus
 * scrape endpoint must respond so orchestrators (liveness/readiness) and monitoring can rely on them.
 *
 * <p>Boots a full servlet context the same way {@link io.miragon.blueprint.openapi.OpenApiSpecExportTest}
 * does — the {@code test} profile is {@code web-application-type=none}, so we override it to {@code servlet} and let
 * the H2 datasource back the built-in {@code db} health indicator (which reports {@code UP}).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.main.web-application-type=servlet"
)
@ActiveProfiles("test")
class ActuatorEndpointsTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    @DisplayName("health endpoint reports UP")
    void healthEndpointReportsUp() throws Exception {
        var response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("liveness probe responds")
    void livenessProbeResponds() throws Exception {
        var response = get("/actuator/health/liveness");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("readiness probe responds")
    void readinessProbeResponds() throws Exception {
        var response = get("/actuator/health/readiness");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("prometheus endpoint exposes metrics")
    void prometheusEndpointExposesMetrics() throws Exception {
        var response = get("/actuator/prometheus");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("# HELP");
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
