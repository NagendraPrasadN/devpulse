package com.devpulse.incident;

import com.devpulse.common.GlobalExceptionHandler;
import com.devpulse.engine.exception.IncidentStateException;
import com.devpulse.engine.fingerprint.ErrorFingerprint;
import com.devpulse.engine.fingerprint.RepeatedError;
import com.devpulse.engine.incident.Incident;
import com.devpulse.engine.incident.IncidentStatus;
import com.devpulse.engine.model.Event;
import com.devpulse.engine.model.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IncidentControllerTest {

    private final IncidentService service = mock(IncidentService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new IncidentController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private Incident incident() {
        Event latest = Event.create("shop", "api", Severity.ERROR, "Timeout after 5ms", "TimeoutException");
        return Incident.open(new RepeatedError(ErrorFingerprint.from(latest), 5, latest), Clock.systemUTC());
    }

    @Test
    void listReturnsIncidentsAsJson() throws Exception {
        Incident incident = incident();
        when(service.list(true)).thenReturn(List.of(incident));

        mvc.perform(get("/api/incidents").param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(incident.id().toString()))
                .andExpect(jsonPath("$[0].projectId").value("shop"))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void acknowledgeReturnsTheUpdatedIncident() throws Exception {
        Incident incident = incident();
        incident.acknowledge("dave");
        when(service.acknowledge(incident.id(), "dave")).thenReturn(incident);

        mvc.perform(post("/api/incidents/{id}/acknowledge", incident.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"by":"dave"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedBy").value("dave"));
    }

    @Test
    void illegalTransitionReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.resolve(id)).thenThrow(
                new IncidentStateException(id, IncidentStatus.RESOLVED, IncidentStatus.RESOLVED));

        mvc.perform(post("/api/incidents/{id}/resolve", id))
                .andExpect(status().isConflict());
    }

    @Test
    void blankAcknowledgerReturns400() throws Exception {
        mvc.perform(post("/api/incidents/{id}/acknowledge", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"by":" "}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void malformedUuidReturns400() throws Exception {
        mvc.perform(get("/api/incidents/not-a-uuid")).andExpect(status().isBadRequest());
    }
}
