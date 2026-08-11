package com.dj1012h.researchpilot.literature.rag.index;

import com.dj1012h.researchpilot.literature.rag.RagPointPayload;
import com.dj1012h.researchpilot.literature.rag.VerifiedPaperProjection;
import com.dj1012h.researchpilot.literature.rag.VerifiedPaperProjectionPlan;
import com.dj1012h.researchpilot.literature.rag.VerifiedPaperProjectionPlanResult;
import com.dj1012h.researchpilot.literature.rag.VerifiedPaperProjectionResult;
import com.dj1012h.researchpilot.literature.rag.VerifiedPaperProjector;
import com.dj1012h.researchpilot.literature.rag.VerifiedPaperSource;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Fail-closed MySQL-to-Qdrant rebuild orchestration for one embedding version. */
public class RagIndexRebuildService {

    private final VerifiedPaperSourceRepository sourceRepository;
    private final VerifiedPaperProjector projector;
    private final RagIndexPort indexPort;
    private final RagIndexStateStore stateStore;
    private final RagIndexDefinition definition;
    private final Clock clock;

    public RagIndexRebuildService(
            VerifiedPaperSourceRepository sourceRepository,
            VerifiedPaperProjector projector,
            RagIndexPort indexPort,
            RagIndexStateStore stateStore,
            RagIndexDefinition definition,
            Clock clock
    ) {
        this.sourceRepository = Objects.requireNonNull(sourceRepository, "sourceRepository must not be null");
        this.projector = Objects.requireNonNull(projector, "projector must not be null");
        this.indexPort = Objects.requireNonNull(indexPort, "indexPort must not be null");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore must not be null");
        this.definition = Objects.requireNonNull(definition, "definition must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public RagIndexRebuildResult rebuild() {
        Instant startedAt = clock.instant();
        boolean buildRecorded = false;
        try {
            stateStore.begin(definition, startedAt);
            buildRecorded = true;
            indexPort.ensureCollection(definition);

            List<VerifiedPaperSource> sources = List.copyOf(sourceRepository.findCurrentlyVerified());
            Map<UUID, RagPointPayload> existing = uniqueByPointId(indexPort.listPayloads(definition));
            Map<Long, Map<UUID, RagPointPayload>> existingByPaper = groupByPaper(existing.values());
            Set<Long> authoritativePaperIds = new LinkedHashSet<>();
            List<VerifiedPaperProjection> projectionsToUpsert = new ArrayList<>();
            List<RagPointPayload> payloadsToReplace = new ArrayList<>();
            Set<UUID> pointIdsToDelete = new LinkedHashSet<>();
            long expectedPointCount = 0;
            int embeddedPapers = 0;
            int skippedPapers = 0;
            RagPointPayload activationSample = null;

            for (VerifiedPaperSource source : sources) {
                if (!authoritativePaperIds.add(source.paperId())) {
                    throw new RagIndexRebuildException(
                            "DUPLICATE_SOURCE_PAPER",
                            "authoritative source returned a duplicate paper identifier");
                }
                VerifiedPaperProjectionPlanResult planResult = projector.prepare(source);
                if (!planResult.admitted()) {
                    throw new RagIndexRebuildException(
                            "SOURCE_REJECTED_" + planResult.rejectionReason().name(),
                            "an authoritative source failed the frozen projection admission boundary");
                }
                VerifiedPaperProjectionPlan plan = planResult.plan();
                if (activationSample == null) {
                    activationSample = plan.points().getFirst();
                }
                expectedPointCount += plan.points().size();
                Map<UUID, RagPointPayload> existingPaper = existingByPaper.getOrDefault(source.paperId(), Map.of());
                Map<UUID, RagPointPayload> planned = uniqueByPointId(plan.points());

                if (allVectorInputsMatch(planned, existingPaper)) {
                    skippedPapers++;
                    for (RagPointPayload desired : planned.values()) {
                        if (!desired.equals(existingPaper.get(desired.pointId()))) {
                            payloadsToReplace.add(desired);
                        }
                    }
                } else {
                    VerifiedPaperProjectionResult projected = projector.project(plan);
                    if (!projected.admitted()) {
                        throw new RagIndexRebuildException(
                                "PREPARED_PLAN_REJECTED",
                                "an admitted projection plan was unexpectedly rejected");
                    }
                    projectionsToUpsert.addAll(projected.projections());
                    embeddedPapers++;
                }
                existingPaper.keySet().stream()
                        .filter(pointId -> !planned.containsKey(pointId))
                        .forEach(pointIdsToDelete::add);
            }

            existing.values().stream()
                    .filter(payload -> !authoritativePaperIds.contains(payload.paperId()))
                    .map(RagPointPayload::pointId)
                    .forEach(pointIdsToDelete::add);

            indexPort.upsert(definition, projectionsToUpsert);
            indexPort.replacePayloads(definition, payloadsToReplace);
            indexPort.deletePoints(definition, List.copyOf(pointIdsToDelete));
            long actualPointCount = indexPort.count(definition);
            if (actualPointCount != expectedPointCount) {
                throw new RagIndexRebuildException(
                        "POINT_COUNT_MISMATCH",
                        "Qdrant point count does not match the authoritative rebuild plan");
            }
            if (activationSample == null) {
                throw new RagIndexRebuildException(
                        "ACTIVATION_SAMPLE_MISSING",
                        "an empty index cannot satisfy the sampled retrieval activation gate");
            }
            indexPort.validateForActivation(definition, activationSample);
            Instant completedAt = clock.instant();
            stateStore.activate(definition, sources.size(), actualPointCount, completedAt);
            return new RagIndexRebuildResult(
                    sources.size(),
                    expectedPointCount,
                    actualPointCount,
                    embeddedPapers,
                    skippedPapers,
                    payloadsToReplace.size(),
                    pointIdsToDelete.size());
        } catch (RuntimeException exception) {
            if (buildRecorded) {
                try {
                    stateStore.fail(definition, failureCode(exception), clock.instant());
                } catch (RuntimeException stateFailure) {
                    exception.addSuppressed(stateFailure);
                }
            }
            throw exception;
        }
    }

    private Map<UUID, RagPointPayload> uniqueByPointId(List<RagPointPayload> payloads) {
        return uniqueByPointId((Iterable<RagPointPayload>) payloads);
    }

    private Map<UUID, RagPointPayload> uniqueByPointId(Iterable<RagPointPayload> payloads) {
        Map<UUID, RagPointPayload> result = new LinkedHashMap<>();
        for (RagPointPayload payload : payloads) {
            RagPointPayload previous = result.put(payload.pointId(), payload);
            if (previous != null) {
                throw new RagIndexRebuildException(
                        "DUPLICATE_POINT_ID",
                        "the rebuild plan contains a duplicate point identifier");
            }
        }
        return result;
    }

    private Map<Long, Map<UUID, RagPointPayload>> groupByPaper(Iterable<RagPointPayload> payloads) {
        Map<Long, Map<UUID, RagPointPayload>> result = new LinkedHashMap<>();
        for (RagPointPayload payload : payloads) {
            result.computeIfAbsent(payload.paperId(), ignored -> new LinkedHashMap<>())
                    .put(payload.pointId(), payload);
        }
        return result;
    }

    private boolean allVectorInputsMatch(
            Map<UUID, RagPointPayload> planned,
            Map<UUID, RagPointPayload> existing
    ) {
        for (RagPointPayload desired : planned.values()) {
            RagPointPayload indexed = existing.get(desired.pointId());
            if (indexed == null
                    || !desired.embeddingModel().equals(indexed.embeddingModel())
                    || !desired.embeddingVersion().equals(indexed.embeddingVersion())
                    || !desired.contentHash().equals(indexed.contentHash())
                    || !desired.text().equals(indexed.text())) {
                return false;
            }
        }
        return true;
    }

    private String failureCode(RuntimeException exception) {
        if (exception instanceof RagIndexRebuildException rebuildException) {
            return rebuildException.failureCode();
        }
        if (exception instanceof RagIndexException indexException) {
            return "QDRANT_" + indexException.failureType().name();
        }
        if (exception instanceof EmbeddingException embeddingException) {
            return "EMBEDDING_" + embeddingException.failureType().name();
        }
        return "UNEXPECTED_" + exception.getClass().getSimpleName().toUpperCase(java.util.Locale.ROOT);
    }
}
