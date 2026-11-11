package com.devpulse.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole platform over real HTTP: register a project, send repeated errors, and wait for the
 * engine (running on its own threads) to open an incident that the REST API then shows.
 *
 * Uses Java's built-in HttpClient and a real server on a random port, so the test touches:
 * Spring MVC -> validation -> services -> engine queue -> worker threads -> detector -> incident.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "devpulse.seed-demo-data=false")
class EndToEndTest {

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        builder.method(method, json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void repeatedErrorsOpenAnIncidentThatTheApiExposes() throws Exception {
        String project = "e2e-" + UUID.randomUUID().toString().substring(0, 8);

        // 1. register
        HttpResponse<String> created = send("POST", "/api/projects",
                "{\"id\":\"%s\",\"name\":\"E2E\",\"owner\":\"tester\"}".formatted(project));
        assertThat(created.statusCode()).isEqualTo(201);

        // 2. registering the same id again is a conflict
        assertThat(send("POST", "/api/projects",
                "{\"id\":\"%s\",\"name\":\"E2E\",\"owner\":\"tester\"}".formatted(project)).statusCode())
                .isEqualTo(409);

        // 3. send 6 errors that differ only in a number -> same fingerprint (threshold is 5)
        for (int i = 0; i < 6; i++) {
            HttpResponse<String> accepted = send("POST", "/api/events",
                    ("{\"projectId\":\"%s\",\"service\":\"api\",\"severity\":\"ERROR\","
                            + "\"message\":\"Timeout after %dms\",\"errorType\":\"TimeoutException\"}")
                            .formatted(project, 100 + i));
            assertThat(accepted.statusCode()).isEqualTo(202);
        }

        // 4. processing is asynchronous: poll until the incident appears (max ~5 s)
        String incidents = "";
        for (int attempt = 0; attempt < 50 && !incidents.contains(project); attempt++) {
            Thread.sleep(100);
            incidents = send("GET", "/api/incidents?activeOnly=true", null).body();
        }
        assertThat(incidents).contains("\"projectId\":\"" + project + "\"");

        // 5. the stored events and the dashboard numbers are visible too
        assertThat(send("GET", "/api/projects/" + project + "/events?minSeverity=ERROR", null).body())
                .contains("TimeoutException");
        assertThat(send("GET", "/api/stats", null).body()).contains("errorsPerProject");

        // 6. an event for an unregistered project is refused up front
        assertThat(send("POST", "/api/events",
                "{\"projectId\":\"ghost-project\",\"service\":\"api\",\"severity\":\"INFO\",\"message\":\"hi\"}")
                .statusCode()).isEqualTo(404);
    }
}
