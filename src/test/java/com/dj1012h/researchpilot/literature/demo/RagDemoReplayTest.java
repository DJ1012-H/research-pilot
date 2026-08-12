package com.dj1012h.researchpilot.literature.demo;

import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.dj1012h.researchpilot.common.ai.ModelInvoker;
import com.dj1012h.researchpilot.literature.application.VerificationPolicy;
import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.dj1012h.researchpilot.literature.rag.RagDocumentBuilder;
import com.dj1012h.researchpilot.literature.rag.RagDocumentSegment;
import com.dj1012h.researchpilot.literature.rag.RagPointIdFactory;
import com.dj1012h.researchpilot.literature.rag.RagPointPayload;
import com.dj1012h.researchpilot.literature.rag.RagSegmentType;
import com.dj1012h.researchpilot.literature.rag.answer.LlmRagAnswerGenerator;
import com.dj1012h.researchpilot.literature.rag.answer.RagAnswerBusinessValidator;
import com.dj1012h.researchpilot.literature.rag.answer.RagAnswerCitationGuard;
import com.dj1012h.researchpilot.literature.rag.answer.RagAnswerDraftMapper;
import com.dj1012h.researchpilot.literature.rag.answer.RagAnswerDraftSchemaValidator;
import com.dj1012h.researchpilot.literature.rag.answer.RagAnswerInput;
import com.dj1012h.researchpilot.literature.rag.answer.RagAnswerProperties;
import com.dj1012h.researchpilot.literature.rag.answer.RagAnswerRepairPromptBuilder;
import com.dj1012h.researchpilot.literature.rag.answer.RagAnswerResponseAssembler;
import com.dj1012h.researchpilot.literature.rag.answer.RagAnswerService;
import com.dj1012h.researchpilot.literature.rag.answer.RagAnswerStatus;
import com.dj1012h.researchpilot.literature.rag.answer.RagAnswerValidationPipeline;
import com.dj1012h.researchpilot.literature.rag.answer.ResearchAnswerResponse;
import com.dj1012h.researchpilot.literature.rag.answer.ResearchQuestionRequest;
import com.dj1012h.researchpilot.literature.rag.answer.UntrustedRagAnswerDraft;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingBatch;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingPort;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingVector;
import com.dj1012h.researchpilot.literature.rag.embedding.RagEmbeddingProfile;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexDefinition;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexException;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexFailureType;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexPort;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexProbe;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexSearchHit;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexSearchRequest;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexStateStore;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexVersionState;
import com.dj1012h.researchpilot.literature.rag.retrieval.RagRetrievalProperties;
import com.dj1012h.researchpilot.literature.rag.retrieval.RagRetrievalRequest;
import com.dj1012h.researchpilot.literature.rag.retrieval.RagRetrievalResult;
import com.dj1012h.researchpilot.literature.rag.retrieval.RagRetrievalService;
import com.dj1012h.researchpilot.literature.rag.retrieval.TrustedPaperReadRepository;
import com.dj1012h.researchpilot.literature.rag.retrieval.TrustedPaperRecord;
import com.dj1012h.researchpilot.literature.rag.retrieval.TrustedRagEvidence;
import com.dj1012h.researchpilot.literature.rag.retrieval.TrustedRagRetrieval;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fixed RAG orchestration replay. Retrieval, MySQL re-admission, evidence
 * numbering, validation, and response assembly are production classes; only
 * the embedding/index/model providers are deterministic local fakes.
 */
class RagDemoReplayTest {

    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    @Test
    void shouldReplayCrossLanguageYearFilterAndInsufficientEvidence() {
        Fixture fixture = new Fixture();

        ResearchAnswerResponse crossLanguage = fixture.answer(new ResearchQuestionRequest(
                "哪些论文讨论了选择性状态空间模型？", 5, null, null, List.of()));
        assertThat(crossLanguage.status()).isEqualTo(RagAnswerStatus.SUCCESS);
        assertThat(crossLanguage.citations()).extracting(citation -> citation.paperId())
                .containsExactly(101L);
        assertThat(crossLanguage.citations().getFirst().normalizedDoi())
                .isEqualTo("10.1000/english-ssm");
        emit("CROSS_LANGUAGE_CITED_ANSWER", crossLanguage);

        RagRetrievalResult allYears = fixture.retrieval().retrieve(
                new RagRetrievalRequest("state space model", 5, null, null, List.of(), List.of()));
        RagRetrievalResult currentYear = fixture.retrieval().retrieve(
                new RagRetrievalRequest("state space model", 5, 2024, 2024, List.of(), List.of()));
        assertThat(allYears.admittedPaperCount()).isEqualTo(2);
        assertThat(currentYear.admittedPaperCount()).isEqualTo(1);
        assertThat(currentYear.results()).extracting(hit -> hit.paperId()).containsExactly(101L);
        ResearchAnswerResponse yearFiltered = fixture.answer(new ResearchQuestionRequest(
                "请只看 2024 年的论文", 5, 2024, 2024, List.of()));
        assertThat(yearFiltered.status()).isEqualTo(RagAnswerStatus.SUCCESS);
        assertThat(yearFiltered.retrievalSummary().evidenceCount()).isOne();
        emit("YEAR_FILTER_EFFECT", yearFiltered);

        ResearchAnswerResponse insufficient = fixture.answer(new ResearchQuestionRequest(
                "没有证据的问题", 5, null, null, List.of(9_999_999L)));
        assertThat(insufficient.status()).isEqualTo(RagAnswerStatus.INSUFFICIENT_EVIDENCE);
        assertThat(insufficient.answer()).isEmpty();
        assertThat(insufficient.citations()).isEmpty();
        assertThat(insufficient.diagnostics().modelCallCount()).isZero();
        assertThat(insufficient.diagnostics().repairCount()).isZero();
        emit("INSUFFICIENT_EVIDENCE", insufficient);

        assertThat(fixture.generator().callCount()).isEqualTo(2);
    }

    private void emit(String scenario, ResearchAnswerResponse response) {
        System.out.printf(
                "[RAG_DEMO_REPLAY] scenario=%s status=%s candidateCount=%d admittedCount=%d "
                        + "evidenceCount=%d modelCallCount=%d repairCount=%d citationCount=%d%n",
                scenario,
                response.status(),
                response.retrievalSummary().qdrantCandidateCount(),
                response.retrievalSummary().admittedPaperCount(),
                response.retrievalSummary().evidenceCount(),
                response.diagnostics().modelCallCount(),
                response.diagnostics().repairCount(),
                response.diagnostics().citationCount());
    }

    private static final class Fixture {
        private static final String VERSION = "fixture-rag-v1";
        private static final int DIMENSIONS = 2;
        private final CountingGenerator generator = new CountingGenerator();
        private final RagRetrievalService retrieval;
        private final RagAnswerService answer;

        private Fixture() {
            PaperFixture first = paper(101L, "10.1000/english-ssm", "Selective State Space Models for Dense Prediction", 2024,
                    "We study selective state space models for efficient dense prediction in remote sensing.");
            PaperFixture second = paper(202L, "10.1000/earlier-ssm", "Efficient Sequence Models for Remote Sensing", 2021,
                    "An earlier study evaluates efficient sequence models for remote sensing prediction.");
            List<PaperFixture> papers = List.of(first, second);

            RagRetrievalProperties retrievalProperties = new RagRetrievalProperties();
            retrievalProperties.setEnabled(true);
            ObjectProvider<EmbeddingPort> embeddings = provider(new FixtureEmbeddingPort());
            ObjectProvider<RagEmbeddingProfile> profile = provider(
                    new RagEmbeddingProfile("fixture-model", VERSION, DIMENSIONS));
            ObjectProvider<RagIndexPort> index = provider(new FixtureIndex(papers));
            ObjectProvider<RagIndexStateStore> state = provider(new FixtureState());
            ObjectProvider<TrustedPaperReadRepository> source = provider(new FixturePaperRepository(papers));
            retrieval = new RagRetrievalService(
                    retrievalProperties, embeddings, profile, index, state, source,
                    new DoiNormalizer(), new RagDocumentBuilder(), Clock.fixed(NOW, ZoneOffset.UTC));

            RagAnswerProperties answerProperties = new RagAnswerProperties();
            answerProperties.setEnabled(true);
            StructuredOutputMapper mapper = new StructuredOutputMapper(
                    new StructuredOutputConfiguration().structuredOutputObjectMapper());
            RagAnswerPromptBuilderForFixture prompt = new RagAnswerPromptBuilderForFixture(mapper, answerProperties);
            RagAnswerValidationPipeline pipeline = new RagAnswerValidationPipeline(
                    mapper,
                    new RagAnswerDraftSchemaValidator(),
                    new RagAnswerDraftMapper(mapper),
                    new RagAnswerBusinessValidator(),
                    new RagAnswerCitationGuard(),
                    answerProperties);
            answer = new RagAnswerService(
                    answerProperties,
                    retrievalProperties,
                    retrieval,
                    prompt.delegate(),
                    new RagAnswerRepairPromptBuilder(prompt.delegate(), answerProperties),
                    generator,
                    pipeline,
                    new RagAnswerResponseAssembler(),
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }

        private ResearchAnswerResponse answer(ResearchQuestionRequest request) {
            return answer.answer(request);
        }

        private RagRetrievalService retrieval() { return retrieval; }

        private CountingGenerator generator() { return generator; }

        @SuppressWarnings("unchecked")
        private static <T> ObjectProvider<T> provider(T value) {
            ObjectProvider<T> provider = mock(ObjectProvider.class);
            when(provider.getIfAvailable()).thenReturn(value);
            return provider;
        }

        private record RagAnswerPromptBuilderForFixture(
                com.dj1012h.researchpilot.literature.rag.answer.RagAnswerPromptBuilder delegate) {
            private RagAnswerPromptBuilderForFixture(StructuredOutputMapper mapper, RagAnswerProperties properties) {
                this(new com.dj1012h.researchpilot.literature.rag.answer.RagAnswerPromptBuilder(mapper, properties));
            }
        }
    }

    private static final class CountingGenerator extends LlmRagAnswerGenerator {
        private int calls;

        private CountingGenerator() {
            super(mock(ModelInvoker.class));
        }

        @Override
        public UntrustedRagAnswerDraft generate(String prompt) {
            calls++;
            return new UntrustedRagAnswerDraft(
                    "{\"statements\":[{\"text\":\"The evidence describes an efficient state space model.\",\"citationIds\":[\"P1\"]}]}");
        }

        private int callCount() { return calls; }
    }

    private record PaperFixture(long paperId, PaperDTO paper, String doi, Instant updatedAt,
                                RagPointPayload payload, TrustedPaperRecord record) { }

    private static PaperFixture paper(long id, String doi, String title, int year, String abstractText) {
        PaperDTO paper = new PaperDTO(
                "W" + id, doi, title,
                List.of(new PaperDTO.Author(null, "Ada Lovelace", null)),
                year, "Trusted RAG Journal", List.of(), "article", null,
                abstractText, "en", List.of("state space"), 0, PaperDTO.LiteratureSource.OPENALEX);
        Instant updatedAt = NOW.minusSeconds(id);
        RagDocumentSegment abstractSegment = new RagDocumentBuilder().build(paper, doi).segments().stream()
                .filter(segment -> segment.segmentType() == RagSegmentType.ABSTRACT)
                .findFirst().orElseThrow();
        RagPointPayload payload = new RagPointPayload(
                RagPointIdFactory.create(id, Fixture.VERSION, RagSegmentType.ABSTRACT, 0),
                id, doi, title, year, paper.venue(), paper.language(),
                VerificationResult.VerificationStatus.VERIFIED, VerificationPolicy.VERSION,
                RagSegmentType.ABSTRACT, 0, "fixture-model", Fixture.VERSION,
                abstractSegment.contentHash(), updatedAt, abstractSegment.text());
        TrustedPaperRecord record = new TrustedPaperRecord(
                id, paper, VerificationResult.VerificationStatus.VERIFIED,
                doi, VerificationPolicy.VERSION, updatedAt);
        return new PaperFixture(id, paper, doi, updatedAt, payload, record);
    }

    private static final class FixtureEmbeddingPort implements EmbeddingPort {
        @Override
        public EmbeddingBatch embed(List<String> controlledTexts) {
            return new EmbeddingBatch(
                    "fixture-model",
                    controlledTexts.stream()
                            .map(ignored -> new EmbeddingVector(List.of(1.0, 0.0)))
                            .toList(),
                    2,
                    Duration.ofMillis(1));
        }
    }

    private static final class FixtureIndex implements RagIndexPort {
        private final List<RagIndexSearchHit> hits;

        private FixtureIndex(List<PaperFixture> papers) {
            hits = papers.stream()
                    .map(paper -> new RagIndexSearchHit(paper.payload(), paper.paperId() == 101L ? 0.95 : 0.80))
                    .sorted(Comparator.comparingDouble(RagIndexSearchHit::score).reversed())
                    .toList();
        }

        @Override public void ensureCollection(RagIndexDefinition definition) { }
        @Override public List<RagPointPayload> listPayloads(RagIndexDefinition definition) {
            return hits.stream().map(RagIndexSearchHit::payload).toList();
        }
        @Override public void upsert(RagIndexDefinition definition, List<com.dj1012h.researchpilot.literature.rag.VerifiedPaperProjection> projections) { }
        @Override public void replacePayloads(RagIndexDefinition definition, List<RagPointPayload> payloads) { }
        @Override public void deletePoints(RagIndexDefinition definition, List<UUID> pointIds) { }
        @Override public long count(RagIndexDefinition definition) { return hits.size(); }
        @Override public void validateForActivation(RagIndexDefinition definition, RagPointPayload sample) { }
        @Override
        public List<RagIndexSearchHit> search(RagIndexDefinition definition, RagIndexSearchRequest request) {
            return hits.stream()
                    .filter(hit -> request.segmentTypes().isEmpty()
                            || request.segmentTypes().contains(hit.payload().segmentType()))
                    .toList();
        }
        @Override public RagIndexProbe probe() { return new RagIndexProbe(true, "fixture index"); }
    }

    private static final class FixtureState implements RagIndexStateStore {
        @Override public void begin(RagIndexDefinition definition, Instant startedAt) { }
        @Override public void activate(RagIndexDefinition definition, int sourcePaperCount, long pointCount, Instant completedAt) { }
        @Override public void fail(RagIndexDefinition definition, String failureCode, Instant completedAt) { }
        @Override
        public Optional<RagIndexVersionState> active() {
            return Optional.of(new RagIndexVersionState(
                    Fixture.VERSION, "fixture_collection", 2, "SUCCEEDED", true,
                    2, 2, null, NOW, NOW, NOW));
        }
    }

    private static final class FixturePaperRepository implements TrustedPaperReadRepository {
        private final List<PaperFixture> papers;

        private FixturePaperRepository(List<PaperFixture> papers) { this.papers = papers; }

        @Override
        public List<TrustedPaperRecord> findByPaperIds(java.util.Collection<Long> paperIds) {
            return papers.stream()
                    .filter(paper -> paperIds.contains(paper.paperId()))
                    .map(PaperFixture::record)
                    .toList();
        }
    }
}
