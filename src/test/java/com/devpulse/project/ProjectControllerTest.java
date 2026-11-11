package com.devpulse.project;

import com.devpulse.common.GlobalExceptionHandler;
import com.devpulse.engine.exception.EntityNotFoundException;
import com.devpulse.engine.model.Project;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WEB-LAYER test: real controller + real GlobalExceptionHandler + real JSON and validation,
 * but a mocked service and NO server and NO Spring context. Fast, and it checks HTTP behavior:
 * status codes, headers, JSON, error format.
 */
class ProjectControllerTest {

    private final ProjectService service = mock(ProjectService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ProjectController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void registerReturns201WithLocationHeader() throws Exception {
        when(service.register("billing", "Billing", "dave"))
                .thenReturn(new Project("billing", "Billing", "dave"));

        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"billing","name":"Billing","owner":"dave"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/projects/billing"))
                .andExpect(jsonPath("$.id").value("billing"))
                .andExpect(jsonPath("$.owner").value("dave"));
    }

    @Test
    void invalidBodyReturns400AndNeverReachesTheService() throws Exception {
        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"Bad Id!","name":"","owner":"dave"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("name")))
                .andExpect(content().string(containsString("id")));

        verifyNoInteractions(service);
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateProjectReturns409() throws Exception {
        when(service.register(any(), any(), any())).thenThrow(new DuplicateProjectException("billing"));

        mvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"billing","name":"Billing","owner":"dave"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void unknownProjectReturns404() throws Exception {
        when(service.get("nope")).thenThrow(new EntityNotFoundException("Project", "nope"));

        mvc.perform(get("/api/projects/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listReturnsAllProjects() throws Exception {
        when(service.findAll()).thenReturn(List.of(
                new Project("chat", "Chat", "bob"), new Project("shop", "Shop", "alice")));

        mvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("chat"));
    }

    @Test
    void unknownUrlStaysA404InsteadOfBecomingA500() throws Exception {
        // the catch-all handler must not swallow Spring's own errors
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isNotFound());
    }
}
