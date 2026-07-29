package com.dj1012h.researchpilot.literature.review;

import com.dj1012h.researchpilot.config.ReviewProperties;
import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.dj1012h.researchpilot.exception.ModelFailureType;
import com.dj1012h.researchpilot.exception.ModelInvocationException;
import com.dj1012h.researchpilot.literature.agent.AgentAction;
import com.dj1012h.researchpilot.literature.agent.AgentRunResult;
import com.dj1012h.researchpilot.literature.agent.AgentStage;
import com.dj1012h.researchpilot.literature.agent.AgentState;
import com.dj1012h.researchpilot.literature.agent.TerminationReason;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceReviewOrchestratorTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-02T00:00:00Z");
    private static final Clock BEFORE_DEADLINE =
            Clock.fixed(STARTED_AT.plusSeconds(10), ZoneOffset.UTC);

    @Test
    void shouldUseOneLogicalCallWhenTheInitialDraftIsValid() {
        EvidenceReviewGenerator generator = mock(EvidenceReviewGenerator.class);
        when(generator.generate(anyString())).thenReturn(draft(validJson("P1", "P3")));

        ReviewOutcome outcome = orchestrator(generator, BEFORE_DEADLINE, new ReviewProperties())
                .generateValidateAndAssemble(runResult(state(formalPapers(), STARTED_AT.plusSeconds(60))));

        assertThat(outcome.status()).isEqualTo(ReviewOutcomeStatus.GENERATED);
        assertThat(outcome.modelCallCount()).isEqualTo(1);
        assertThat(outcome.repairCount()).isZero();
        assertThat(outcome.validatedReview().orElseThrow().statements().getFirst().citationIds())
                .extracting(CitationId::value)
                .containsExactly("P1", "P3");
        verify(generator, times(1)).generate(anyString());
    }

    @Test
    void shouldRepairUnknownCitationOnceAndRevalidateTheWholeDraft() {
        EvidenceReviewGenerator generator = mock(EvidenceReviewGenerator.class);
        when(generator.generate(anyString()))
                .thenReturn(draft(validJson("P999")), draft(validJson("P3", "P4")));

        ReviewOutcome outcome = orchestrator(generator, BEFORE_DEADLINE, new ReviewProperties())
                .generateValidateAndAssemble(runResult(state(formalPapers(), STARTED_AT.plusSeconds(60))));

        assertThat(outcome.status()).isEqualTo(ReviewOutcomeStatus.GENERATED);
        assertThat(outcome.modelCallCount()).isEqualTo(2);
        assertThat(outcome.repairCount()).isEqualTo(1);
        assertThat(outcome.validatedReview().orElseThrow().statements().getFirst().citationIds())
                .extracting(CitationId::value)
                .containsExactly("P3", "P4");
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(generator, times(2)).generate(prompts.capture());
        assertThat(prompts.getAllValues().get(1))
                .contains("UNKNOWN_CITATION_ID", "This is the only correction opportunity.")
                .doesNotContain("Review draft validation failed");
    }

    @Test
    void shouldStopAfterTwoInvalidDraftsWithoutPublishingPartialText() {
        EvidenceReviewGenerator generator = mock(EvidenceReviewGenerator.class);
        when(generator.generate(anyString()))
                .thenReturn(draft(validJson("P999")), draft(validJson("P999")));

        ReviewOutcome outcome = orchestrator(generator, BEFORE_DEADLINE, new ReviewProperties())
                .generateValidateAndAssemble(runResult(state(formalPapers(), STARTED_AT.plusSeconds(60))));

        assertThat(outcome.status()).isEqualTo(ReviewOutcomeStatus.VALIDATION_FAILED);
        assertThat(outcome.modelCallCount()).isEqualTo(2);
        assertThat(outcome.repairCount()).isEqualTo(1);
        assertThat(outcome.validatedReview()).isEmpty();
        assertThat(outcome.reviewInput()).isEmpty();
        verify(generator, times(2)).generate(anyString());
    }

    @Test
    void shouldRepairInvalidJsonOnlyOnceAndThenSucceed() {
        EvidenceReviewGenerator generator = mock(EvidenceReviewGenerator.class);
        when(generator.generate(anyString()))
                .thenReturn(draft("{not-json"), draft(validJson("P1")));

        ReviewOutcome outcome = orchestrator(generator, BEFORE_DEADLINE, new ReviewProperties())
                .generateValidateAndAssemble(runResult(state(formalPapers(), STARTED_AT.plusSeconds(60))));

        assertThat(outcome.status()).isEqualTo(ReviewOutcomeStatus.GENERATED);
        assertThat(outcome.modelCallCount()).isEqualTo(2);
        verify(generator, times(2)).generate(anyString());
    }

    @Test
    void shouldRejectAFormalPaperWithoutAbstractAsReviewEvidenceThenRepair() {
        EvidenceReviewGenerator generator = mock(EvidenceReviewGenerator.class);
        when(generator.generate(anyString()))
                .thenReturn(draft(validJson("P2")), draft(validJson("P1", "P4")));

        ReviewOutcome outcome = orchestrator(generator, BEFORE_DEADLINE, new ReviewProperties())
                .generateValidateAndAssemble(runResult(state(formalPapers(), STARTED_AT.plusSeconds(60))));

        assertThat(outcome.status()).isEqualTo(ReviewOutcomeStatus.GENERATED);
        assertThat(outcome.reviewInput().orElseThrow().evidencePapers())
                .extracting(paper -> paper.citationId().value())
                .containsExactly("P1", "P3", "P4");
        verify(generator, times(2)).generate(anyString());
    }

    @Test
    void shouldMakeZeroCallsWhenEvidenceGateFails() {
        EvidenceReviewGenerator generator = mock(EvidenceReviewGenerator.class);

        ReviewOutcome outcome = orchestrator(generator, BEFORE_DEADLINE, new ReviewProperties())
                .generateValidateAndAssemble(runResult(state(List.of(), STARTED_AT.plusSeconds(60))));

        assertThat(outcome.status()).isEqualTo(ReviewOutcomeStatus.INSUFFICIENT_EVIDENCE);
        assertThat(outcome.modelCallCount()).isZero();
        verify(generator, never()).generate(anyString());
    }

    @Test
    void shouldMakeZeroCallsWhenTheInputBudgetCannotFitThreePapers() {
        EvidenceReviewGenerator generator = mock(EvidenceReviewGenerator.class);
        ReviewProperties properties = new ReviewProperties();
        StructuredOutputMapper mapper = mapper();
        ReviewEvidenceSerializer serializer = new ReviewEvidenceSerializer(mapper);
        ReviewInput firstTwo = new ReviewInput(5, 4, 2, List.of(
                evidencePaper(1, "abstract 1"),
                evidencePaper(3, "abstract 3")
        ));
        properties.setMaxEvidenceJsonLength(serializer.serialize(firstTwo).length());

        ReviewOutcome outcome = orchestrator(generator, BEFORE_DEADLINE, properties)
                .generateValidateAndAssemble(runResult(state(formalPapers(), STARTED_AT.plusSeconds(60))));

        assertThat(outcome.status()).isEqualTo(ReviewOutcomeStatus.INPUT_BUDGET_EXCEEDED);
        assertThat(outcome.modelCallCount()).isZero();
        verify(generator, never()).generate(anyString());
    }

    @Test
    void shouldNotTreatProviderFailureAsCitationRepair() {
        EvidenceReviewGenerator generator = mock(EvidenceReviewGenerator.class);
        when(generator.generate(anyString())).thenThrow(new ModelInvocationException(
                ModelFailureType.TIMEOUT, new RuntimeException("SENSITIVE_PROVIDER_DETAIL")));

        ReviewOutcome outcome = orchestrator(generator, BEFORE_DEADLINE, new ReviewProperties())
                .generateValidateAndAssemble(runResult(state(formalPapers(), STARTED_AT.plusSeconds(60))));

        assertThat(outcome.status()).isEqualTo(ReviewOutcomeStatus.GENERATION_UNAVAILABLE);
        assertThat(outcome.modelCallCount()).isEqualTo(1);
        assertThat(outcome.repairCount()).isZero();
        assertThat(outcome.failureCode()).contains("GENERATION_UNAVAILABLE");
        verify(generator, times(1)).generate(anyString());
    }

    @Test
    void shouldFailClosedForAnUnexpectedGeneratorRuntimeFailure() {
        EvidenceReviewGenerator generator = mock(EvidenceReviewGenerator.class);
        when(generator.generate(anyString()))
                .thenThrow(new IllegalStateException("SENSITIVE_UNEXPECTED_GENERATOR_DETAIL"));

        ReviewOutcome outcome = orchestrator(generator, BEFORE_DEADLINE, new ReviewProperties())
                .generateValidateAndAssemble(runResult(state(formalPapers(), STARTED_AT.plusSeconds(60))));

        assertThat(outcome.status()).isEqualTo(ReviewOutcomeStatus.GENERATION_UNAVAILABLE);
        assertThat(outcome.failureCode()).contains("GENERATION_UNAVAILABLE");
        assertThat(outcome.failureCode().orElseThrow())
                .doesNotContain("SENSITIVE_UNEXPECTED_GENERATOR_DETAIL");
        verify(generator, times(1)).generate(anyString());
    }

    @Test
    void shouldMakeZeroCallsWhenDeadlineHasArrived() {
        EvidenceReviewGenerator generator = mock(EvidenceReviewGenerator.class);
        Clock atDeadline = Clock.fixed(STARTED_AT.plusSeconds(60), ZoneOffset.UTC);

        ReviewOutcome outcome = orchestrator(generator, atDeadline, new ReviewProperties())
                .generateValidateAndAssemble(runResult(state(formalPapers(), STARTED_AT.plusSeconds(60))));

        assertThat(outcome.status()).isEqualTo(ReviewOutcomeStatus.DEADLINE_EXCEEDED);
        assertThat(outcome.modelCallCount()).isZero();
        verify(generator, never()).generate(anyString());
    }

    @Test
    void shouldNotStartRepairWhenDeadlineArrivesAfterTheInitialCall() {
        EvidenceReviewGenerator generator = mock(EvidenceReviewGenerator.class);
        when(generator.generate(anyString())).thenReturn(draft(validJson("P999")));
        Clock clock = mock(Clock.class);
        when(clock.instant())
                .thenReturn(STARTED_AT.plusSeconds(10), STARTED_AT.plusSeconds(60));

        ReviewOutcome outcome = orchestrator(generator, clock, new ReviewProperties())
                .generateValidateAndAssemble(runResult(state(formalPapers(), STARTED_AT.plusSeconds(60))));

        assertThat(outcome.status()).isEqualTo(ReviewOutcomeStatus.DEADLINE_EXCEEDED);
        assertThat(outcome.modelCallCount()).isEqualTo(1);
        assertThat(outcome.repairCount()).isZero();
        verify(generator, times(1)).generate(anyString());
    }

    private EvidenceReviewOrchestrator orchestrator(
            EvidenceReviewGenerator generator,
            Clock clock,
            ReviewProperties properties
    ) {
        StructuredOutputMapper mapper = mapper();
        ReviewEvidenceSerializer serializer = new ReviewEvidenceSerializer(mapper);
        ReviewGenerationService generationService = new ReviewGenerationService(
                new ReviewInputFactory(new DoiNormalizer()),
                new EvidenceReviewPromptBuilder(serializer, properties),
                generator
        );
        ReviewDraftValidationPipeline pipeline = new ReviewDraftValidationPipeline(
                mapper,
                new ReviewDraftSchemaValidator(),
                new ReviewDraftMapper(mapper),
                new ReviewDraftBusinessValidator(),
                new CitationGuard(new CitationIdParser()),
                properties
        );
        return new EvidenceReviewOrchestrator(
                generationService,
                new ReviewInputBudgeter(properties, serializer),
                new EvidenceReviewRepairPromptBuilder(serializer, properties),
                pipeline,
                clock
        );
    }

    private StructuredOutputMapper mapper() {
        return new StructuredOutputMapper(
                new StructuredOutputConfiguration().structuredOutputObjectMapper());
    }

    private AgentRunResult runResult(AgentState state) {
        AgentRunResult runResult = mock(AgentRunResult.class);
        when(runResult.finalState()).thenReturn(state);
        return runResult;
    }

    private AgentState state(
            List<SearchResponse.PaperResult> formalPapers,
            Instant deadline
    ) {
        return new AgentState(
                "safe query",
                5,
                null,
                List.of(),
                AgentStage.COMPLETED,
                AgentAction.COMPLETE,
                List.of(),
                List.of(),
                List.of(),
                formalPapers,
                0,
                0,
                0,
                0,
                0,
                Set.of(),
                0,
                List.of(),
                STARTED_AT,
                deadline,
                STARTED_AT.plusSeconds(1),
                TerminationReason.PARTIAL_RESULTS,
                "fixture completed"
        );
    }

    private List<SearchResponse.PaperResult> formalPapers() {
        return List.of(
                formal(1, "abstract 1"),
                formal(2, null),
                formal(3, "abstract 3"),
                formal(4, "abstract 4")
        );
    }

    private SearchResponse.PaperResult formal(int position, String abstractText) {
        PaperDTO paper = new PaperDTO(
                "https://openalex.org/W" + position,
                "10.1000/" + position,
                "Evidence title " + position,
                List.of(new PaperDTO.Author(
                        "https://openalex.org/A" + position,
                        "Author " + position,
                        null
                )),
                2025,
                "Venue",
                List.of(),
                "article",
                null,
                abstractText,
                "en",
                List.of(),
                0,
                PaperDTO.LiteratureSource.OPENALEX
        );
        return new SearchResponse.PaperResult(
                paper,
                0.8,
                new VerificationResult(
                        VerificationResult.VerificationStatus.VERIFIED,
                        1.0,
                        VerificationResult.VerificationSource.CROSSREF,
                        paper.doi(),
                        List.of(),
                        List.of()
                )
        );
    }

    private EvidencePaper evidencePaper(int position, String abstractText) {
        return new EvidencePaper(
                new CitationId(position),
                "10.1000/" + position,
                "Evidence title " + position,
                List.of("Author " + position),
                2025,
                "Venue",
                abstractText
        );
    }

    private UntrustedReviewDraft draft(String raw) {
        return new UntrustedReviewDraft(raw);
    }

    private String validJson(String... citationIds) {
        try {
            return mapper().writeValueAsString(new ReviewDraft(List.of(
                    new ReviewStatement(
                            ReviewStatementType.OBSERVATION,
                            "The abstracts describe a bounded observation.",
                            List.of(citationIds)
                    )
            )));
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new AssertionError(exception);
        }
    }
}
