package com.dj1012h.researchpilot.literature.rag.index;

import com.dj1012h.researchpilot.literature.application.VerificationPolicy;
import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.dj1012h.researchpilot.literature.rag.RagDocumentBuilder;
import com.dj1012h.researchpilot.literature.rag.RagPointPayload;
import com.dj1012h.researchpilot.literature.rag.VerifiedPaperProjection;
import com.dj1012h.researchpilot.literature.rag.VerifiedPaperProjector;
import com.dj1012h.researchpilot.literature.rag.VerifiedPaperSource;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingBatch;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingPort;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingVector;
import com.dj1012h.researchpilot.literature.rag.embedding.RagEmbeddingProfile;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagIndexRebuildServiceTest {

    private static final RagEmbeddingProfile PROFILE = new RagEmbeddingProfile("test-model", "test-v1", 2);
    private static final RagIndexDefinition DEFINITION = new RagIndexDefinition("test_collection", "test-v1", 2);
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    void shouldRebuildIdempotentlySkipUnchangedEmbeddingsAndRemoveDowngradedPapers() {
        MutableSourceRepository sources = new MutableSourceRepository(List.of(
                source(1L, "10.1000/one", "Title one", NOW, VerificationResult.VerificationStatus.VERIFIED),
                source(2L, "10.1000/two", "Title two", NOW, VerificationResult.VerificationStatus.VERIFIED)));
        CountingEmbeddingPort embeddings = new CountingEmbeddingPort();
        InMemoryIndexPort index = new InMemoryIndexPort();
        RecordingStateStore state = new RecordingStateStore();
        RagIndexRebuildService service = service(sources, embeddings, index, state);

        RagIndexRebuildResult first = service.rebuild();
        RagIndexRebuildResult second = service.rebuild();

        assertThat(first.sourcePaperCount()).isEqualTo(2);
        assertThat(first.actualPointCount()).isEqualTo(4);
        assertThat(first.embeddedPaperCount()).isEqualTo(2);
        assertThat(second.actualPointCount()).isEqualTo(4);
        assertThat(second.embeddedPaperCount()).isZero();
        assertThat(second.skippedEmbeddingPaperCount()).isEqualTo(2);
        assertThat(embeddings.callCount).isEqualTo(2);

        sources.sources = List.of(
                source(1L, "10.1000/one", "Changed title one", NOW.plusSeconds(1),
                        VerificationResult.VerificationStatus.VERIFIED),
                source(2L, "10.1000/two", "Title two", NOW,
                        VerificationResult.VerificationStatus.VERIFIED));
        RagIndexRebuildResult changed = service.rebuild();

        assertThat(changed.embeddedPaperCount()).isEqualTo(1);
        assertThat(changed.skippedEmbeddingPaperCount()).isEqualTo(1);
        assertThat(embeddings.callCount).isEqualTo(3);
        assertThat(index.payloads.values()).allMatch(payload -> payload.paperId() == 1L
                ? payload.title().equals("Changed title one")
                : payload.title().equals("Title two"));

        sources.sources = List.of(source(
                1L,
                "10.1000/one",
                "Changed title one",
                NOW.plusSeconds(1),
                VerificationResult.VerificationStatus.VERIFIED));
        RagIndexRebuildResult downgraded = service.rebuild();

        assertThat(downgraded.actualPointCount()).isEqualTo(2);
        assertThat(downgraded.deletedPointCount()).isEqualTo(2);
        assertThat(index.payloads.values()).allMatch(payload -> payload.paperId() == 1L);
        assertThat(state.lastFailureCode).isNull();
        assertThat(state.active().orElseThrow().pointCount()).isEqualTo(2);
    }

    @Test
    void shouldRefreshPayloadTimestampWithoutRepeatingEmbedding() {
        MutableSourceRepository sources = new MutableSourceRepository(List.of(source(
                1L, "10.1000/one", "Title one", NOW, VerificationResult.VerificationStatus.VERIFIED)));
        CountingEmbeddingPort embeddings = new CountingEmbeddingPort();
        InMemoryIndexPort index = new InMemoryIndexPort();
        RagIndexRebuildService service = service(sources, embeddings, index, new RecordingStateStore());
        service.rebuild();

        sources.sources = List.of(source(
                1L, "10.1000/one", "Title one", NOW.plusSeconds(5),
                VerificationResult.VerificationStatus.VERIFIED));
        RagIndexRebuildResult result = service.rebuild();

        assertThat(result.embeddedPaperCount()).isZero();
        assertThat(result.payloadOnlyUpdateCount()).isEqualTo(2);
        assertThat(embeddings.callCount).isEqualTo(1);
        assertThat(index.payloads.values())
                .allMatch(payload -> payload.sourceUpdatedAt().equals(NOW.plusSeconds(5)));
    }

    @Test
    void shouldRecordFailureAndRetryAfterAPartialUpsert() {
        MutableSourceRepository sources = new MutableSourceRepository(List.of(source(
                1L, "10.1000/one", "Title one", NOW, VerificationResult.VerificationStatus.VERIFIED)));
        CountingEmbeddingPort embeddings = new CountingEmbeddingPort();
        InMemoryIndexPort index = new InMemoryIndexPort();
        index.failNextUpsertPartially = true;
        RecordingStateStore state = new RecordingStateStore();
        RagIndexRebuildService service = service(sources, embeddings, index, state);

        assertThatThrownBy(service::rebuild)
                .isInstanceOfSatisfying(RagIndexException.class,
                        exception -> assertThat(exception.failureType())
                                .isEqualTo(RagIndexFailureType.HTTP_FAILURE));
        assertThat(state.lastFailureCode).isEqualTo("QDRANT_HTTP_FAILURE");
        assertThat(index.payloads).hasSize(1);

        RagIndexRebuildResult retry = service.rebuild();

        assertThat(retry.actualPointCount()).isEqualTo(2);
        assertThat(index.payloads).hasSize(2);
        assertThat(state.lastFailureCode).isNull();
    }

    @Test
    void shouldRejectANonVerifiedAuthoritativeRowBeforeEmbeddingOrWrite() {
        MutableSourceRepository sources = new MutableSourceRepository(List.of(source(
                1L, "10.1000/one", "Title one", NOW,
                VerificationResult.VerificationStatus.PARTIALLY_VERIFIED)));
        CountingEmbeddingPort embeddings = new CountingEmbeddingPort();
        InMemoryIndexPort index = new InMemoryIndexPort();
        RecordingStateStore state = new RecordingStateStore();

        assertThatThrownBy(() -> service(sources, embeddings, index, state).rebuild())
                .isInstanceOfSatisfying(RagIndexRebuildException.class,
                        exception -> assertThat(exception.failureCode())
                                .isEqualTo("SOURCE_REJECTED_STATUS_NOT_VERIFIED"));
        assertThat(embeddings.callCount).isZero();
        assertThat(index.payloads).isEmpty();
        assertThat(state.lastFailureCode).isEqualTo("SOURCE_REJECTED_STATUS_NOT_VERIFIED");
    }

    @Test
    void shouldNotActivateAnEmptyIndexWithoutASampledRetrieval() {
        MutableSourceRepository sources = new MutableSourceRepository(List.of());
        CountingEmbeddingPort embeddings = new CountingEmbeddingPort();
        InMemoryIndexPort index = new InMemoryIndexPort();
        RecordingStateStore state = new RecordingStateStore();

        assertThatThrownBy(() -> service(sources, embeddings, index, state).rebuild())
                .isInstanceOfSatisfying(RagIndexRebuildException.class,
                        exception -> assertThat(exception.failureCode())
                                .isEqualTo("ACTIVATION_SAMPLE_MISSING"));
        assertThat(state.active()).isEmpty();
        assertThat(state.lastFailureCode).isEqualTo("ACTIVATION_SAMPLE_MISSING");
        assertThat(embeddings.callCount).isZero();
    }

    private RagIndexRebuildService service(
            MutableSourceRepository sources,
            CountingEmbeddingPort embeddings,
            InMemoryIndexPort index,
            RecordingStateStore state
    ) {
        VerifiedPaperProjector projector = new VerifiedPaperProjector(
                new DoiNormalizer(),
                new RagDocumentBuilder(),
                embeddings,
                PROFILE);
        return new RagIndexRebuildService(
                sources,
                projector,
                index,
                state,
                DEFINITION,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private VerifiedPaperSource source(
            long paperId,
            String doi,
            String title,
            Instant updatedAt,
            VerificationResult.VerificationStatus status
    ) {
        PaperDTO paper = new PaperDTO(
                "W" + paperId,
                doi,
                title,
                List.of(new PaperDTO.Author(null, "Ada Lovelace", null)),
                2026,
                "Journal",
                List.of(),
                "article",
                null,
                "Controlled abstract.",
                "en",
                List.of(),
                0,
                PaperDTO.LiteratureSource.OPENALEX);
        VerificationResult verification = new VerificationResult(
                status,
                status == VerificationResult.VerificationStatus.VERIFIED ? 1.0 : null,
                VerificationResult.VerificationSource.CROSSREF,
                doi,
                List.of(),
                List.of("TEST"));
        return new VerifiedPaperSource(
                paperId,
                paper,
                verification,
                doi,
                VerificationPolicy.VERSION,
                updatedAt);
    }

    private static final class MutableSourceRepository implements VerifiedPaperSourceRepository {
        private List<VerifiedPaperSource> sources;
        private MutableSourceRepository(List<VerifiedPaperSource> sources) { this.sources = sources; }
        @Override public List<VerifiedPaperSource> findCurrentlyVerified() { return List.copyOf(sources); }
    }

    private static final class CountingEmbeddingPort implements EmbeddingPort {
        private int callCount;
        @Override
        public EmbeddingBatch embed(List<String> controlledTexts) {
            callCount++;
            List<EmbeddingVector> vectors = new ArrayList<>();
            for (int index = 0; index < controlledTexts.size(); index++) {
                vectors.add(new EmbeddingVector(Collections.nCopies(2, (double) (callCount + index))));
            }
            return new EmbeddingBatch(PROFILE.model(), vectors, 2, Duration.ofMillis(1));
        }
    }

    private static final class InMemoryIndexPort implements RagIndexPort {
        private final Map<UUID, RagPointPayload> payloads = new LinkedHashMap<>();
        private boolean failNextUpsertPartially;

        @Override public void ensureCollection(RagIndexDefinition definition) { }
        @Override public List<RagPointPayload> listPayloads(RagIndexDefinition definition) {
            return List.copyOf(payloads.values());
        }
        @Override
        public void upsert(RagIndexDefinition definition, List<VerifiedPaperProjection> projections) {
            if (failNextUpsertPartially && !projections.isEmpty()) {
                payloads.put(projections.getFirst().pointId(), projections.getFirst().payload());
                failNextUpsertPartially = false;
                throw new RagIndexException(RagIndexFailureType.HTTP_FAILURE, "simulated partial failure");
            }
            projections.forEach(projection -> payloads.put(projection.pointId(), projection.payload()));
        }
        @Override public void replacePayloads(RagIndexDefinition definition, List<RagPointPayload> replacements) {
            replacements.forEach(payload -> payloads.put(payload.pointId(), payload));
        }
        @Override public void deletePoints(RagIndexDefinition definition, List<UUID> pointIds) {
            pointIds.forEach(payloads::remove);
        }
        @Override public long count(RagIndexDefinition definition) { return payloads.size(); }
        @Override public void validateForActivation(RagIndexDefinition definition, RagPointPayload sample) {
            assertThat(payloads).containsEntry(sample.pointId(), sample);
        }
        @Override
        public List<com.dj1012h.researchpilot.literature.rag.index.RagIndexSearchHit> search(
                RagIndexDefinition definition,
                com.dj1012h.researchpilot.literature.rag.index.RagIndexSearchRequest request
        ) {
            return List.of();
        }
        @Override public RagIndexProbe probe() { return new RagIndexProbe(true, "test"); }
    }

    private static final class RecordingStateStore implements RagIndexStateStore {
        private RagIndexVersionState active;
        private String lastFailureCode;
        @Override public void begin(RagIndexDefinition definition, Instant startedAt) { }
        @Override
        public void activate(RagIndexDefinition definition, int sourcePaperCount, long pointCount, Instant completedAt) {
            active = new RagIndexVersionState(
                    definition.embeddingVersion(), definition.collectionName(), definition.vectorDimensions(),
                    "SUCCEEDED", true, sourcePaperCount, pointCount, null,
                    completedAt, completedAt, completedAt);
            lastFailureCode = null;
        }
        @Override public void fail(RagIndexDefinition definition, String failureCode, Instant completedAt) {
            lastFailureCode = failureCode;
        }
        @Override public Optional<RagIndexVersionState> active() { return Optional.ofNullable(active); }
    }
}
