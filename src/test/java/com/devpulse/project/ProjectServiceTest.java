package com.devpulse.project;

import com.devpulse.audit.AuditService;
import com.devpulse.engine.exception.EntityNotFoundException;
import com.devpulse.engine.model.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * UNIT test with Mockito: no Spring, no database. The two collaborators are fakes (mocks), so we
 * test ONLY ProjectService's own decisions: what it calls, in what situation.
 * (The real transaction behavior is proven in TransactionPropagationTest.)
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    ProjectStore store;

    @Mock
    AuditService audit;

    @InjectMocks
    ProjectService service;      // Mockito passes the two mocks into the constructor

    @Test
    void registerInsertsProjectThenAuditsInsideTheSameTransaction() {
        Project saved = service.register("shop", "Shop Backend", "alice");

        assertThat(saved).isEqualTo(new Project("shop", "Shop Backend", "alice"));
        verify(store).insert(new Project("shop", "Shop Backend", "alice"));
        verify(audit).recordWithinTransaction("PROJECT_REGISTERED", "shop", "owner=alice");
        verifyNoMoreInteractions(audit);
    }

    @Test
    void duplicateProjectIsAuditedIndependentlyAndRejected() {
        doThrow(new DuplicateKeyException("duplicate")).when(store).insert(any(Project.class));

        assertThatThrownBy(() -> service.register("shop", "Shop Backend", "alice"))
                .isInstanceOf(DuplicateProjectException.class)
                .hasMessageContaining("shop");

        verify(audit).recordIndependently("PROJECT_REJECTED", "shop", "duplicate id");
        verify(audit, never()).recordWithinTransaction(any(), any(), any());
    }

    @Test
    void getThrowsNotFoundForUnknownProject() {
        when(store.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("ghost")).isInstanceOf(EntityNotFoundException.class);
    }
}
