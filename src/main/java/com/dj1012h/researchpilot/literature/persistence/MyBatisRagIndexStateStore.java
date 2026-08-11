package com.dj1012h.researchpilot.literature.persistence;

import com.dj1012h.researchpilot.literature.persistence.entity.RagIndexVersionEntity;
import com.dj1012h.researchpilot.literature.persistence.mapper.RagPersistenceMapper;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexDefinition;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexStateStore;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexVersionState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.literature.persistence.enabled", havingValue = "true")
public class MyBatisRagIndexStateStore implements RagIndexStateStore {

    private final RagPersistenceMapper mapper;

    public MyBatisRagIndexStateStore(RagPersistenceMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    @Transactional
    public void begin(RagIndexDefinition definition, Instant startedAt) {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        if (mapper.findVersion(definition.embeddingVersion()) == null) {
            if (mapper.insertBuilding(definition, startedAt) != 1) {
                throw new LiteraturePersistenceException("index build state insert failed");
            }
        } else if (mapper.updateBuilding(definition, startedAt) != 1) {
            throw new LiteraturePersistenceException("index build state update failed");
        }
    }

    @Override
    @Transactional
    public void activate(
            RagIndexDefinition definition,
            int sourcePaperCount,
            long pointCount,
            Instant completedAt
    ) {
        mapper.deactivateOtherVersions(definition.embeddingVersion());
        if (mapper.activate(definition, sourcePaperCount, pointCount, completedAt) != 1) {
            throw new LiteraturePersistenceException("index version activation failed");
        }
    }

    @Override
    @Transactional
    public void fail(RagIndexDefinition definition, String failureCode, Instant completedAt) {
        Objects.requireNonNull(failureCode, "failureCode must not be null");
        String bounded = failureCode.length() <= 128 ? failureCode : failureCode.substring(0, 128);
        if (mapper.markFailed(definition, bounded, completedAt) != 1) {
            throw new LiteraturePersistenceException("index failure state update failed");
        }
    }

    @Override
    public Optional<RagIndexVersionState> active() {
        return Optional.ofNullable(mapper.findActiveVersion()).map(this::toState);
    }

    private RagIndexVersionState toState(RagIndexVersionEntity entity) {
        return new RagIndexVersionState(
                entity.embeddingVersion(), entity.collectionName(), entity.vectorDimensions(),
                entity.lastBuildStatus(), entity.active(), entity.sourcePaperCount(), entity.pointCount(),
                entity.lastFailureCode(), entity.buildStartedAt(), entity.buildCompletedAt(), entity.activatedAt());
    }
}
