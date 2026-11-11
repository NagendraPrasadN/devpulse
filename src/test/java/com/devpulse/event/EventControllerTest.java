package com.devpulse.event;

import com.devpulse.common.GlobalExceptionHandler;
import com.devpulse.common.QueueFullException;
import com.devpulse.engine.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EventControllerTest {

    private static final String VALID = """
            {"projectId":"shop","service":"api","severity":"ERROR","message":"Timeout after 12ms",
             "errorType":"TimeoutException"}""";

    private final EventService service = mock(EventService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new EventController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void validEventIsAcceptedWith202() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.ingest(any())).thenReturn(id);

        mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void missingSeverityReturns400() throws Exception {
        mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"shop","service":"api","message":"boom"}"""))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void unknownSeverityValueReturns400() throws Exception {
        mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"shop","service":"api","severity":"BOOM","message":"x"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownProjectReturns404() throws Exception {
        when(service.ingest(any())).thenThrow(new EntityNotFoundException("Project", "shop"));

        mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isNotFound());
    }

    @Test
    void fullQueueReturns503() throws Exception {
        when(service.ingest(any())).thenThrow(new QueueFullException("Event queue is full"));

        mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void invalidSeverityQueryParameterReturns400() throws Exception {
        mvc.perform(get("/api/projects/shop/events").param("minSeverity", "BOOM"))
                .andExpect(status().isBadRequest());
    }
}
