package com.devpulse.integration;

import com.devpulse.audit.AuditService;
import com.devpulse.project.DuplicateProjectException;
import com.devpulse.project.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * INTEGRATION test: the real Spring context, the real H2 database, the real transaction manager.
 * This is the only way to prove propagation rules, because they are implemented by Spring's
 * proxies and the database, not by our code.
 *
 * Each test uses a unique subject id so tests never interfere with each other.
 * The demo seeder is switched off so no fake apps run during tests.
 */
@SpringBootTest(properties = "devpulse.seed-demo-data=false")
class TransactionPropagationTest {

    @Autowired
    AuditService audit;

    @Autowired
    ProjectService projects;

    @Autowired
    PlatformTransactionManager transactionManager;

    private static String uniqueId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void mandatoryPropagationRefusesToRunWithoutATransaction() {
        assertThatThrownBy(() -> audit.recordWithinTransaction("X", uniqueId("m"), "no tx"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void rollbackErasesJoinedRowsButNotRequiresNewRows() {
        String subject = uniqueId("rollback");
        TransactionTemplate outer = new TransactionTemplate(transactionManager);

        outer.executeWithoutResult(status -> {
            audit.recordWithinTransaction("JOINED", subject, "part of the outer transaction");
            audit.recordIndependently("INDEPENDENT", subject, "own transaction, commits immediately");
            status.setRollbackOnly();     // the outer transaction is rolled back
        });

        assertThat(audit.findBySubject(subject))
                .extracting(AuditService.AuditEntry::action)
                .containsExactly("INDEPENDENT");
    }

    @Test
    void successfulRegistrationCommitsProjectAndAuditTogether() {
        String id = uniqueId("ok");

        projects.register(id, "Some Project", "alice");

        assertThat(projects.exists(id)).isTrue();
        assertThat(audit.findBySubject(id))
                .extracting(AuditService.AuditEntry::action)
                .containsExactly("PROJECT_REGISTERED");
    }

    @Test
    void duplicateRegistrationIsRejectedButTheRejectionIsStillRecorded() {
        String id = uniqueId("dup");
        projects.register(id, "First", "alice");

        assertThatThrownBy(() -> projects.register(id, "Second", "bob"))
                .isInstanceOf(DuplicateProjectException.class);

        assertThat(projects.get(id).name()).isEqualTo("First");     // original untouched
        assertThat(audit.findBySubject(id))
                .extracting(AuditService.AuditEntry::action)
                .containsExactly("PROJECT_REGISTERED", "PROJECT_REJECTED");
    }
}
