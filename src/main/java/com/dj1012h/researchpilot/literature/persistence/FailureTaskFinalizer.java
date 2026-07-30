package com.dj1012h.researchpilot.literature.persistence;

import com.dj1012h.researchpilot.literature.persistence.entity.LiteratureSearchTaskEntity;
import com.dj1012h.researchpilot.literature.persistence.mapper.LiteraturePersistenceMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Separate bean so failure finalization always gets a real short transaction. */
@Component
public class FailureTaskFinalizer {

    private final LiteraturePersistenceMapper mapper;

    public FailureTaskFinalizer(LiteraturePersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeFailure(UUID taskId, String failureCode, Instant completedAt) {
        Long databaseId = mapper.findTaskDatabaseId(taskId.toString());
        if (databaseId == null) {
            throw new LiteraturePersistenceException("running task is missing for failure finalization");
        }
        LiteratureSearchTaskEntity failed = new LiteratureSearchTaskEntity(
                taskId.toString(), "FAILED", "NOT_STARTED", safeCode(failureCode),
                1, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                "0".repeat(64), 0, completedAt, completedAt);
        if (mapper.updateTaskFinal(failed) != 1) {
            throw new LiteraturePersistenceException("failure finalization did not update its task");
        }
    }

    private String safeCode(String value) {
        String normalized = value == null || value.isBlank() ? "PERSISTENCE_OR_RUNTIME_FAILURE" : value.trim();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }
}
