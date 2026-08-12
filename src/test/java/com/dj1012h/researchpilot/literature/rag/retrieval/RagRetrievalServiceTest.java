package com.dj1012h.researchpilot.literature.rag.retrieval;

import com.dj1012h.researchpilot.literature.application.VerificationPolicy;
import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.dj1012h.researchpilot.literature.rag.RagDocumentBuilder;
import com.dj1012h.researchpilot.literature.rag.RagDocumentSegment;
import com.dj1012h.researchpilot.literature.rag.RagPointPayload;
import com.dj1012h.researchpilot.literature.rag.RagSegmentType;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingBatch;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingPort;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingVector;
import com.dj1012h.researchpilot.literature.rag.embedding.RagEmbeddingProfile;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexDefinition;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexPort;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexSearchHit;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexStateStore;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexVersionState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RagRetrievalServiceTest {

    private static final Instant SOURCE_UPDATED_AT = Instant.parse("2026-08-10T00:00:00Z");
    private static final String DOI = "10.1000/trusted";

    @Test
    void shouldRemainClosedByDefaultWithoutTouchingDependencies() {
        RagRetrievalProperties properties = properties(false);
        ObjectProvider<EmbeddingPort> embedding = mock(ObjectProvider.class);
        ObjectProvider<RagIndexPort> index = mock(ObjectProvider.class);
        ObjectProvider<RagEmbeddingProfile> profile = mock(ObjectProvider.class);
        RagRetrievalService service = service(properties, embedding, profile, index, mockState(Optional.empty()), repository(null));

        RagRetrievalResult result = service.retrieve(new RagRetrievalRequest("semantic query", null, null, null, null, null));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.diagnostics().failureCode()).isEqualTo("RAG_RETRIEVAL_DISABLED");
        verifyNoInteractions(embedding, index);
    }

    @Test
    void shouldReAdmitOnlyCurrentMysqlPaperAndUseItsBusinessFields() {
        PaperDTO paper = paper();
        RagDocumentSegment metadata = new RagDocumentBuilder().build(paper, DOI).segments().getFirst();
        RagDocumentSegment abstractSegment = new RagDocumentBuilder().build(paper, DOI).segments().get(1);
        RagIndexSearchHit metadataHit = new RagIndexSearchHit(payload(metadata, "Qdrant forged title", SOURCE_UPDATED_AT), 0.60);
        RagIndexSearchHit abstractHit = new RagIndexSearchHit(payload(abstractSegment, "Qdrant forged title", SOURCE_UPDATED_AT), 0.90);
        TrustedPaperRecord current = record(paper, VerificationResult.VerificationStatus.VERIFIED, SOURCE_UPDATED_AT);
        Fixture fixture = fixture(current, List.of(metadataHit, abstractHit), 2);

        RagRetrievalResult result = fixture.service().retrieve(
                new RagRetrievalRequest("  semantic\tquery ", 5, 2021, 2026, List.of(7L), List.of(RagSegmentType.ABSTRACT)));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.qdrantCandidateCount()).isEqualTo(2);
        assertThat(result.uniquePaperCandidateCount()).isEqualTo(1);
        assertThat(result.admittedPaperCount()).isEqualTo(1);
        assertThat(result.results()).singleElement().satisfies(hit -> {
            assertThat(hit.paperId()).isEqualTo(7L);
            assertThat(hit.title()).isEqualTo("Trusted title");
            assertThat(hit.normalizedDoi()).isEqualTo(DOI);
            assertThat(hit.matchedSegmentType()).isEqualTo(RagSegmentType.ABSTRACT);
            assertThat(hit.boundedExcerpt()).contains("Abstract: current abstract");
        });
    }

    @Test
    void shouldRejectDowngradedAndStaleCandidatesWithoutFillingTopK() {
        PaperDTO paper = paper();
        RagDocumentSegment metadata = new RagDocumentBuilder().build(paper, DOI).segments().getFirst();
        TrustedPaperRecord downgraded = record(
                paper,
                VerificationResult.VerificationStatus.PARTIALLY_VERIFIED,
                SOURCE_UPDATED_AT);
        RagIndexSearchHit stale = new RagIndexSearchHit(
                payload(metadata, paper.title(), SOURCE_UPDATED_AT.minusSeconds(1)), 0.80);
        Fixture fixture = fixture(downgraded, List.of(stale), 2);

        RagRetrievalResult result = fixture.service().retrieve(
                new RagRetrievalRequest("semantic query", 5, null, null, List.of(), List.of()));

        assertThat(result.status()).isEqualTo("NO_TRUSTED_RESULTS");
        assertThat(result.admittedPaperCount()).isZero();
        assertThat(result.filteredCount()).isEqualTo(1);
        assertThat(result.diagnostics().failureCode()).isEqualTo("RAG_NO_TRUSTED_RESULTS");
    }

    @Test
    void shouldUseReconstructedMysqlSegmentInsteadOfQdrantPayloadText() {
        PaperDTO paper = paper();
        RagDocumentSegment abstractSegment = new RagDocumentBuilder().build(paper, DOI).segments().get(1);
        RagIndexSearchHit forged = new RagIndexSearchHit(
                payload(abstractSegment, "Qdrant forged title", SOURCE_UPDATED_AT, "QDRANT_PAYLOAD_TEXT_FORGED"),
                0.90);
        Fixture fixture = fixture(record(paper, VerificationResult.VerificationStatus.VERIFIED, SOURCE_UPDATED_AT), List.of(forged), 2);

        RagRetrievalResult result = fixture.service().retrieve(
                new RagRetrievalRequest("semantic query", 5, null, null, List.of(), List.of(RagSegmentType.ABSTRACT)));

        assertThat(result.results()).singleElement().satisfies(hit ->
                assertThat(hit.boundedExcerpt())
                        .contains("Abstract: current abstract")
                        .doesNotContain("QDRANT_PAYLOAD_TEXT_FORGED"));
    }

    @Test
    void shouldFailClosedOnInvalidQueryAndEmbeddingDimension() {
        RagRetrievalService invalidService = fixture(null, List.of(), 2).service();
        RagRetrievalResult invalid = invalidService.retrieve(
                new RagRetrievalRequest(" ", 5, null, null, List.of(), List.of()));
        assertThat(invalid.diagnostics().failureCode()).isEqualTo("RAG_QUERY_INVALID");

        Fixture dimensionFixture = fixture(null, List.of(), 3);
        RagRetrievalResult mismatch = dimensionFixture.service().retrieve(
                new RagRetrievalRequest("semantic query", 5, null, null, List.of(), List.of()));
        assertThat(mismatch.diagnostics().failureCode()).isEqualTo("RAG_EMBEDDING_DIMENSION_MISMATCH");
    }

    private Fixture fixture(TrustedPaperRecord current, List<RagIndexSearchHit> hits, int activeDimensions) {
        RagRetrievalProperties properties = properties(true);
        ObjectProvider<EmbeddingPort> embeddingProvider = mock(ObjectProvider.class);
        EmbeddingPort embedding = texts -> new EmbeddingBatch(
                "test-model",
                List.of(new EmbeddingVector(List.of(1.0, 0.0))),
                2,
                Duration.ofMillis(1));
        when(embeddingProvider.getIfAvailable()).thenReturn(embedding);
        ObjectProvider<RagEmbeddingProfile> profileProvider = mock(ObjectProvider.class);
        when(profileProvider.getIfAvailable()).thenReturn(new RagEmbeddingProfile("test-model", "test-v1", activeDimensions));

        ObjectProvider<RagIndexPort> indexProvider = mock(ObjectProvider.class);
        RagIndexPort index = mock(RagIndexPort.class);
        when(indexProvider.getIfAvailable()).thenReturn(index);
        when(index.search(any(), any())).thenReturn(hits);

        ObjectProvider<RagIndexStateStore> stateProvider = mock(ObjectProvider.class);
        RagIndexStateStore state = mockState(Optional.of(activeState(activeDimensions)));
        when(stateProvider.getIfAvailable()).thenReturn(state);

        ObjectProvider<TrustedPaperReadRepository> repositoryProvider = mock(ObjectProvider.class);
        TrustedPaperReadRepository paperRepository = repository(current);
        when(repositoryProvider.getIfAvailable()).thenReturn(paperRepository);
        RagRetrievalService service = new RagRetrievalService(
                properties,
                embeddingProvider,
                profileProvider,
                indexProvider,
                stateProvider,
                repositoryProvider,
                new DoiNormalizer(),
                new RagDocumentBuilder(),
                Clock.fixed(SOURCE_UPDATED_AT, ZoneOffset.UTC));
        return new Fixture(service, index);
    }

    private RagRetrievalService service(
            RagRetrievalProperties properties,
            ObjectProvider<EmbeddingPort> embedding,
            ObjectProvider<RagEmbeddingProfile> profile,
            ObjectProvider<RagIndexPort> index,
            RagIndexStateStore state,
            TrustedPaperReadRepository repository
    ) {
        ObjectProvider<RagIndexStateStore> stateProvider = mock(ObjectProvider.class);
        when(stateProvider.getIfAvailable()).thenReturn(state);
        ObjectProvider<TrustedPaperReadRepository> repositoryProvider = mock(ObjectProvider.class);
        when(repositoryProvider.getIfAvailable()).thenReturn(repository);
        return new RagRetrievalService(
                properties, embedding, profile, index, stateProvider, repositoryProvider,
                new DoiNormalizer(), new RagDocumentBuilder(), Clock.systemUTC());
    }

    private RagRetrievalProperties properties(boolean enabled) {
        RagRetrievalProperties properties = new RagRetrievalProperties();
        properties.setEnabled(enabled);
        return properties;
    }

    private RagIndexStateStore mockState(Optional<RagIndexVersionState> active) {
        RagIndexStateStore state = mock(RagIndexStateStore.class);
        when(state.active()).thenReturn(active);
        return state;
    }

    private RagIndexVersionState activeState(int dimensions) {
        return new RagIndexVersionState(
                "test-v1", "test_collection", dimensions, "SUCCEEDED", true,
                1, 2, null, SOURCE_UPDATED_AT, SOURCE_UPDATED_AT, SOURCE_UPDATED_AT);
    }

    private TrustedPaperReadRepository repository(TrustedPaperRecord current) {
        return paperIds -> current == null ? List.of() : List.of(current);
    }

    private TrustedPaperRecord record(
            PaperDTO paper,
            VerificationResult.VerificationStatus status,
            Instant sourceUpdatedAt
    ) {
        return new TrustedPaperRecord(7L, paper, status, DOI, VerificationPolicy.VERSION, sourceUpdatedAt);
    }

    private RagPointPayload payload(RagDocumentSegment segment, String title, Instant updatedAt) {
        return payload(segment, title, updatedAt, segment.text());
    }

    private RagPointPayload payload(RagDocumentSegment segment, String title, Instant updatedAt, String text) {
        return new RagPointPayload(
                com.dj1012h.researchpilot.literature.rag.RagPointIdFactory.create(
                        7L, "test-v1", segment.segmentType(), segment.segmentIndex()),
                7L,
                DOI,
                title,
                2024,
                "Qdrant forged venue",
                "en",
                VerificationResult.VerificationStatus.VERIFIED,
                VerificationPolicy.VERSION,
                segment.segmentType(),
                segment.segmentIndex(),
                "test-model",
                "test-v1",
                segment.contentHash(),
                updatedAt,
                text);
    }

    private PaperDTO paper() {
        return new PaperDTO(
                "W7", DOI, "Trusted title",
                List.of(new PaperDTO.Author(null, "Ada Lovelace", null)),
                2024, "Trusted venue", List.of(), "article", null,
                "current abstract", "en", List.of("semantic"), 0,
                PaperDTO.LiteratureSource.OPENALEX);
    }

    private record Fixture(RagRetrievalService service, RagIndexPort index) { }
}
