package com.devpulse.project;

import com.devpulse.engine.model.Project;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * REST API for projects.
 *
 *   POST /api/projects        register a project            -> 201 Created + Location header
 *   GET  /api/projects        list all                      -> 200
 *   GET  /api/projects/{id}   one project                   -> 200, or 404
 *
 * The controller does HTTP work only: read the request, call the service, shape the response.
 * Errors are not handled here; exceptions fly up to GlobalExceptionHandler.
 *
 * DTOs (request/response records) live next to the controller that owns them. We do NOT expose
 * the domain Project directly: the API shape can then evolve independently of the domain model.
 * @Valid makes Spring check the annotations on the request BEFORE the method runs; a failure
 * throws MethodArgumentNotValidException -> 400 with per-field messages.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    public record CreateProjectRequest(
            @NotBlank
            @Pattern(regexp = "[a-z0-9-]{2,40}",
                    message = "must be 2-40 characters: lowercase letters, digits or '-'")
            String id,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) String owner) {
    }

    public record ProjectResponse(String id, String name, String owner) {
        static ProjectResponse from(Project project) {
            return new ProjectResponse(project.id(), project.name(), project.owner());
        }
    }

    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> register(@Valid @RequestBody CreateProjectRequest request) {
        Project project = projects.register(request.id(), request.name(), request.owner());
        return ResponseEntity
                .created(URI.create("/api/projects/" + project.id()))
                .body(ProjectResponse.from(project));
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projects.findAll().stream().map(ProjectResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@PathVariable String id) {
        return ProjectResponse.from(projects.get(id));
    }
}
