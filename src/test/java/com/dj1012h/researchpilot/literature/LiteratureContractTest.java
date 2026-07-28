package com.dj1012h.researchpilot.literature;

import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.application.SearchPlanDraft;
import com.dj1012h.researchpilot.literature.application.SearchPlanGenerationContext;
import com.dj1012h.researchpilot.literature.model.LanguageCode;
import com.dj1012h.researchpilot.literature.model.PaperDTO;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import com.dj1012h.researchpilot.literature.model.VerificationResult;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiteratureContractTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void shouldRejectInvalidSearchRequestFields() {
        SearchRequest request = new SearchRequest(" ", 1899, 1898, 51);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("query", "fromYear", "toYear", "limit");
    }

    @Test
    void shouldAllowStructuredFiltersToBeOmitted() {
        SearchRequest request = new SearchRequest("Mamba 遥感变化检测", null, null, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shouldCreateImmutableExecutableSearchPlan() {
        List<String> keywords = new ArrayList<>(List.of(
                "Mamba",
                "remote sensing change detection"
        ));
        Set<LanguageCode> languages = new LinkedHashSet<>(Set.of(LanguageCode.EN));
        List<String> publicationTypes = new ArrayList<>(List.of("article", "review"));

        SearchPlan plan = new SearchPlan(
                "近五年基于 Mamba 的遥感变化检测文章",
                "remote sensing change detection with Mamba",
                keywords,
                "Mamba remote sensing change detection",
                languages,
                publicationTypes,
                SearchSort.RELEVANCE,
                2022,
                2026,
                20,
                10
        );
        keywords.add("state space model");
        languages.add(LanguageCode.ZH);
        publicationTypes.add("preprint");

        assertThat(plan.englishKeywords())
                .containsExactly("Mamba", "remote sensing change detection");
        assertThat(plan.languages()).containsExactly(LanguageCode.EN);
        assertThat(plan.sort()).isEqualTo(SearchSort.RELEVANCE);
        assertThat(plan.publicationTypes()).containsExactly("article", "review");
        assertThatThrownBy(() -> plan.englishKeywords().add("another keyword"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectUnsafeSearchPlanLimits() {
        assertThatThrownBy(() -> new SearchPlan(
                "query",
                "topic",
                List.of("keyword"),
                "keyword",
                Set.of(),
                List.of(),
                SearchSort.RELEVANCE,
                2022,
                2026,
                5,
                10
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidateLimit");
    }

    @Test
    void shouldRepresentSuccessfulSearchWithNoVerifiedResults() {
        SearchPlan plan = new SearchPlan(
                "近五年基于 Mamba 的遥感变化检测文章",
                "remote sensing change detection with Mamba",
                List.of("Mamba", "remote sensing change detection"),
                "Mamba remote sensing change detection",
                Set.of(LanguageCode.EN),
                List.of("article", "review"),
                SearchSort.RELEVANCE,
                2022,
                2026,
                20,
                10
        );

        SearchResponse response = new SearchResponse(
                UUID.randomUUID(),
                SearchResponse.SearchStatus.NO_VERIFIED_RESULTS,
                plan,
                20,
                12,
                new SearchResponse.VerificationSummary(0, 0, 4, 8),
                List.of(),
                "未找到同时满足主题相关性和最低核验标准的论文",
                1_200,
                Instant.parse("2026-07-19T08:30:00Z")
        );

        assertThat(response.status()).isEqualTo(SearchResponse.SearchStatus.NO_VERIFIED_RESULTS);
        assertThat(response.papers()).isEmpty();
        assertThat(response.verificationSummary().unverifiedCount()).isEqualTo(4);
        assertThat(response.verificationSummary().rejectedCount()).isEqualTo(8);
    }

    @Test
    void shouldKeepPaperMetadataSeparateFromVerificationEvidence() {
        PaperDTO paper = new PaperDTO(
                "W3177828909",
                "10.1038/s41586-021-03819-2",
                "Highly accurate protein structure prediction with AlphaFold",
                List.of(new PaperDTO.Author(null, "John Jumper", null)),
                2021,
                "Nature",
                List.of("0028-0836"),
                "article",
                "https://doi.org/10.1038/s41586-021-03819-2",
                null,
                "en",
                List.of("protein structure prediction", "deep learning"),
                0,
                PaperDTO.LiteratureSource.OPENALEX
        );
        VerificationResult verification = new VerificationResult(
                VerificationResult.VerificationStatus.VERIFIED,
                0.95,
                VerificationResult.VerificationSource.CROSSREF,
                paper.doi(),
                List.of(),
                List.of("DOI 与标题均得到 Crossref 支持")
        );

        SearchResponse.PaperResult result = new SearchResponse.PaperResult(
                paper,
                0.91,
                verification
        );

        assertThat(result.paper()).isSameAs(paper);
        assertThat(result.verification().status())
                .isEqualTo(VerificationResult.VerificationStatus.VERIFIED);
    }

    @Test
    void shouldRejectUncheckedPaperFromFormalResponse() {
        PaperDTO paper = new PaperDTO(
                "W1",
                "10.1000/example",
                "Example paper",
                List.of(new PaperDTO.Author(null, "Example Author", null)),
                2026,
                "Example Venue",
                List.of(),
                "article",
                "https://doi.org/10.1000/example",
                null,
                "en",
                List.of("example"),
                0,
                PaperDTO.LiteratureSource.OPENALEX
        );

        assertThatThrownBy(() -> new SearchResponse.PaperResult(
                paper,
                0.90,
                VerificationResult.notChecked()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最低核验标准");
    }

    @Test
    void shouldRejectPartiallyVerifiedPaperFromFormalResponse() {
        PaperDTO paper = new PaperDTO(
                "W1", "10.1000/example", "Example paper",
                List.of(new PaperDTO.Author(null, "Example Author", null)), 2026, "Example Venue", List.of(),
                "article", null, null, "en", List.of(), 0, PaperDTO.LiteratureSource.OPENALEX);
        VerificationResult partial = new VerificationResult(
                VerificationResult.VerificationStatus.PARTIALLY_VERIFIED, null,
                VerificationResult.VerificationSource.CROSSREF, null, List.of(), List.of("ambiguous"));

        assertThatThrownBy(() -> new SearchResponse.PaperResult(paper, 0.90, partial))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowPartiallyVerifiedDiagnosticsWithNoFormalPapers() {
        SearchPlan plan = new SearchPlan("query", "topic", List.of("keyword"), "keyword", Set.of(), List.of(),
                SearchSort.RELEVANCE, 2022, 2026, 10, 5);

        SearchResponse response = new SearchResponse(UUID.randomUUID(), SearchResponse.SearchStatus.NO_VERIFIED_RESULTS,
                plan, 1, 1, new SearchResponse.VerificationSummary(0, 1, 0, 0), List.of(), "no formal papers",
                1, Instant.parse("2026-07-19T08:30:00Z"));

        assertThat(response.verificationSummary().partiallyVerifiedCount()).isOne();
    }

    @Test
    void shouldRejectInconsistentVerificationSummary() {
        SearchPlan plan = new SearchPlan(
                "query",
                "topic",
                List.of("keyword"),
                "keyword",
                Set.of(),
                List.of(),
                SearchSort.RELEVANCE,
                2022,
                2026,
                10,
                5
        );

        assertThatThrownBy(() -> new SearchResponse(
                UUID.randomUUID(),
                SearchResponse.SearchStatus.COMPLETED,
                plan,
                10,
                8,
                new SearchResponse.VerificationSummary(2, 1, 1, 3),
                List.of(),
                "检索完成",
                100,
                Instant.parse("2026-07-19T08:30:00Z")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("deduplicatedCount");
    }

    @Test
    void shouldRejectEvidenceScoreOutsideSupportedRange() {
        assertThatThrownBy(() -> new VerificationResult(
                VerificationResult.VerificationStatus.VERIFIED,
                1.01,
                VerificationResult.VerificationSource.CROSSREF,
                "10.1000/example",
                List.of(),
                List.of("invalid test score")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidenceScore");
    }

    @Test
    void shouldKeepUntrustedDraftSeparateFromExecutablePlan() {
        SearchPlanDraft draft = new SearchPlanDraft(
                "remote sensing change detection",
                List.of("Mamba", "remote sensing"),
                "Mamba remote sensing change detection",
                List.of("en"),
                List.of("article"),
                "newest",
                5,
                null,
                null,
                10
        );

        assertThat(draft.languages()).containsExactly("en");
        assertThat(draft.sort()).isEqualTo("newest");
        assertThat(Arrays.stream(SearchPlanDraft.class.getRecordComponents())
                .map(component -> component.getName()))
                .doesNotContain("originalQuery", "candidateLimit");
    }

    @Test
    void shouldCreateStableGenerationContextFromInjectedClock() {
        SearchRequest request = new SearchRequest("Mamba 遥感变化检测", null, null, null);
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-20T08:00:00Z"),
                ZoneOffset.UTC
        );

        SearchPlanGenerationContext context = SearchPlanGenerationContext.create(request, clock);

        assertThat(context.request()).isSameAs(request);
        assertThat(context.startedAt()).isEqualTo(Instant.parse("2026-07-20T08:00:00Z"));
        assertThat(context.currentYear()).isEqualTo(2026);
        assertThat(context.requestId()).isNotNull();
    }
}
