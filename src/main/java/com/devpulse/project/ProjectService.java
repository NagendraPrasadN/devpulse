package com.devpulse.project;

import com.devpulse.audit.AuditService;
import com.devpulse.engine.exception.EntityNotFoundException;
import com.devpulse.engine.model.Project;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for projects. The SERVICE layer:
 *   controller  -> HTTP only (parse request, choose status code)
 *   service     -> rules and TRANSACTION boundaries      <- you are here
 *   store       -> SQL only
 *
 * Constructor injection: Spring sees one constructor, finds a bean for each parameter, and
 * passes them in. Fields are final, so the object is never half-built, and a unit test can
 * simply call new ProjectService(mockStore, mockAudit).
 */
@Service
public class ProjectService {

    private final ProjectStore store;
    private final AuditService audit;

    public ProjectService(ProjectStore store, AuditService audit) {
        this.store = store;
        this.audit = audit;
    }

    /**
     * ONE transaction (REQUIRED, the default) covers everything inside:
     *   success: project row + audit row are committed together, or neither is.
     *   duplicate: the project insert fails. We write a REJECTED audit row with REQUIRES_NEW
     *              (its own transaction, committed at once), THEN throw. The exception rolls back
     *              this method's transaction, but the rejection record survives.
     *
     * Spring rolls back automatically for any RuntimeException (all DevPulseExceptions are).
     */
    @Transactional
    public Project register(String id, String name, String owner) {
        Project project = new Project(id, name, owner);
        try {
            store.insert(project);
        } catch (DuplicateKeyException e) {
            audit.recordIndependently("PROJECT_REJECTED", id, "duplicate id");
            throw new DuplicateProjectException(id);
        }
        audit.recordWithinTransaction("PROJECT_REGISTERED", id, "owner=" + owner);
        return project;
    }

    @Transactional(readOnly = true)
    public Project get(String id) {
        return store.findById(id).orElseThrow(() -> new EntityNotFoundException("Project", id));
    }

    @Transactional(readOnly = true)
    public List<Project> findAll() {
        return store.findAll();
    }

    /** Called by the engine's validator for every event (on worker threads). */
    @Transactional(readOnly = true)
    public boolean exists(String id) {
        return store.exists(id);
    }
}
