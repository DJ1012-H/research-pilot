package com.dj1012h.researchpilot.literature.review;

import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.dj1012h.researchpilot.literature.agent.AgentAction;
import com.dj1012h.researchpilot.literature.agent.AgentStage;
import com.dj1012h.researchpilot.literature.agent.AgentState;
import com.dj1012h.researchpilot.literature.agent.TerminationReason;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReviewGenerationServiceTest {

    @Test
    void shouldGenerateOnceWhenFormalAndAbstractGatesAreExactlyMet() {
        EvidenceReviewGenerator generator = mock(EvidenceReviewGenerator.class);
        when(generator.generate(anyString())).thenReturn(new UntrustedReviewDraft("untrusted text"));
        ReviewGenerationService service = service(generator);

        ReviewGenerationAttempt attempt = service.prepareAndGenerate(state(5, List.of(
                formal("a", "usable abstract A"),
                formal("b", "usable abstract B"),
                formal("c", "usable abstract C")
        )));

        assertThat(attempt.preparation().eligibility()).isEqualTo(ReviewEligibility.ELIGIBLE);
        assertThat(attempt.preparation().requiredVerifiedCount()).isEqualTo(3);
        assertThat(attempt.preparation().reviewInput().orElseThrow().evidencePapers())
                .extracting(paper -> paper.citationId().value())
                .containsExactly("P1", "P2", "P3");
        assertThat(attempt.untrustedDraft()).contains(new UntrustedReviewDraft("untrusted text"));
        verify(generator, times(1)).generate(anyString());
    }

    @Test
    void shouldNotCallGeneratorWhenVerifiedPaperGateFails() {
        EvidenceReviewGenerator generator = mock(EvidenceReviewGenerator.class);

        ReviewGenerationAttempt attempt = service(generator).prepareAndGenerate(state(10, List.of(
                formal("a", "abstract A"), formal("b", "abstract B"), formal("c", "abstract C"),
                formal("d", "abstract D"), formal("e", "abstract E")
        )));

        assertThat(attempt.preparation().eligibility())
                .isEqualTo(ReviewEligibility.INSUFFICIENT_VERIFIED_PAPERS);
        assertThat(attempt.preparation().requiredVerifiedCount()).isEqualTo(6);
        assertThat(attempt.preparation().reviewInput()).isEmpty();
        assertThat(attempt.untrustedDraft()).isEmpty();
        verifyNoInteractions(generator);
    }

    @Test
    void shouldNotCallGeneratorWhenAbstractEvidenceGateFails() {
        EvidenceReviewGenerator generator = mock(EvidenceReviewGenerator.class);

        ReviewGenerationAttempt attempt = service(generator).prepareAndGenerate(state(5, List.of(
                formal("a", "abstract A"), formal("b", "abstract B"), formal("c", null),
                formal("d", " "), formal("e", null)
        )));

        assertThat(attempt.preparation().eligibility()).isEqualTo(ReviewEligibility.INSUFFICIENT_ABSTRACTS);
        assertThat(attempt.preparation().abstractEvidenceCount()).isEqualTo(2);
        verifyNoInteractions(generator);
    }

    @Test
    void shouldNotCallGeneratorWhenThereAreNoFormalPapers() {
        EvidenceReviewGenerator generator = mock(EvidenceReviewGenerator.class);

        ReviewGenerationAttempt attempt = service(generator).prepareAndGenerate(state(5, List.of()));

        assertThat(attempt.preparation().eligibility())
                .isEqualTo(ReviewEligibility.INSUFFICIENT_VERIFIED_PAPERS);
        assertThat(attempt.preparation().reviewInput()).isEmpty();
        verifyNoInteractions(generator);
    }

    @Test
    void shouldKeepFormalPositionsWhenPapersWithoutAbstractsAreExcludedFromEvidence() {
        ReviewInputFactory factory = new ReviewInputFactory(new DoiNormalizer());

        ReviewPreparationResult result = factory.prepare(state(5, List.of(
                formal("a", "abstract A"), formal("b", null), formal("c", "abstract C"),
                formal("d", "abstract D")
        )));

        assertThat(result.eligibility()).isEqualTo(ReviewEligibility.ELIGIBLE);
        assertThat(result.reviewInput().orElseThrow().evidencePapers())
                .extracting(paper -> paper.citationId().value())
                .containsExactly("P1", "P3", "P4");
    }

    @Test
    void shouldRejectAnInProgressAgentStateBeforeAnyEvidencePreparation() {
        ReviewInputFactory factory = new ReviewInputFactory(new DoiNormalizer());

        assertThatThrownBy(() -> factory.prepare(AgentState.initialize(
                "safe query", 5, Clock.systemUTC(), Duration.ofSeconds(30)
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completed final state");
    }

    private ReviewGenerationService service(EvidenceReviewGenerator generator) {
        StructuredOutputMapper mapper = new StructuredOutputMapper(
                new StructuredOutputConfiguration().structuredOutputObjectMapper()
        );
        return new ReviewGenerationService(
                new ReviewInputFactory(new DoiNormalizer()),
                new EvidenceReviewPromptBuilder(mapper),
                generator
        );
    }

    private AgentState state(int requestedCount, List<SearchResponse.PaperResult> formalPapers) {
        Instant started = Instant.parse("2026-08-02T00:00:00Z");
        return new AgentState(
                "safe query", requestedCount, null, List.of(), AgentStage.COMPLETED, AgentAction.COMPLETE,
                List.of(), List.of(), List.of(), formalPapers,
                0, 0, 0, 0, 0, Set.of(), 0, List.of(),
                started, started.plusSeconds(60), started.plusSeconds(1),
                TerminationReason.NO_VERIFIED_RESULTS, "fixture completed"
        );
    }

    private SearchResponse.PaperResult formal(String suffix, String abstractText) {
        PaperDTO paper = new PaperDTO(
                "https://openalex.org/W" + suffix,
                "10.1000/" + suffix,
                "Title " + suffix,
                List.of(new PaperDTO.Author("https://openalex.org/A" + suffix, "Author " + suffix, null)),
                2025,
                "Venue " + suffix,
                List.of(),
                "article",
                null,
                abstractText,
                "en",
                List.of(),
                0,
                PaperDTO.LiteratureSource.OPENALEX
        );
        return new SearchResponse.PaperResult(paper, 0.8, verified());
    }

    private VerificationResult verified() {
        return new VerificationResult(
                VerificationResult.VerificationStatus.VERIFIED,
                1.0,
                VerificationResult.VerificationSource.CROSSREF,
                "10.1000/fixture",
                List.of(),
                List.of()
        );
    }
}
