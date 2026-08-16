package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.AgentBudgetProperties;
import com.dj1012h.researchpilot.config.AiProperties;
import com.dj1012h.researchpilot.config.LiteratureSearchProperties;
import com.dj1012h.researchpilot.config.StructuredOutputConfiguration;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchPort;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.application.SearchPlanDraft;
import com.dj1012h.researchpilot.literature.application.SearchPlanGenerationContext;
import com.dj1012h.researchpilot.literature.application.ValidatedSearchPlanContext;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchPlanValidationResult;
import com.dj1012h.researchpilot.literature.validation.JsonSyntaxValidator;
import com.dj1012h.researchpilot.literature.validation.SearchPlanBusinessValidator;
import com.dj1012h.researchpilot.literature.validation.SearchPlanDraftMapper;
import com.dj1012h.researchpilot.literature.validation.SearchPlanSchemaValidator;
import com.dj1012h.researchpilot.literature.validation.SearchPlanSecurityValidator;
import com.dj1012h.researchpilot.literature.validation.SearchPlanValidationException;
import com.dj1012h.researchpilot.literature.validation.SearchPlanValidationPipeline;
import com.dj1012h.researchpilot.literature.validation.ValidationIssue;
import com.dj1012h.researchpilot.literature.validation.ValidationStage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchPlanRefinerTest {

    private final AiProperties aiProperties = new AiProperties();
    private final LiteratureSearchProperties searchProperties = new LiteratureSearchProperties();
    private final StructuredOutputMapper mapper = new StructuredOutputMapper(
            new StructuredOutputConfiguration().structuredOutputObjectMapper()
    );

    @Test
    void shouldAppendLegalExpressionsInStableOrderAndPreserveEveryFrozenField() {
        SearchPlanRefinementContext context = context(0);
        SearchPlan current = context.current().validationResult().plan();

        SearchPlanRefinementResult result = refiner(planPipeline()).refine(
                context,
                new SearchPlanRefinementDraft(
                        List.of("state space model", "visual mamba"),
                        List.of("SSM", "RSCD"),
                        List.of("bi-temporal image change detection"),
                        "Broaden terminology after insufficient verified results"
                )
        );

        assertThat(result.refinedPlan().englishKeywords()).containsExactly(
                "mamba",
                "remote sensing change detection",
                "state space model",
                "visual mamba",
                "SSM",
                "RSCD",
                "bi-temporal image change detection"
        );
        assertFrozenFields(result.refinedPlan(), current);
        assertThat(result.refinedPlan().searchQuery())
                .isEqualTo(
                        "mamba remote sensing change detection"
                                + " OR state space model OR visual mamba OR SSM OR RSCD"
                                + " OR bi-temporal image change detection"
                );
        assertThat(result.origins()).isEqualTo(context.current().validationResult().origins());
        assertThat(result.diff().addedKeywords()).containsExactly(
                "state space model",
                "visual mamba",
                "SSM",
                "RSCD",
                "bi-temporal image change detection"
        );
        assertThat(result.diff().removedKeywords()).isEmpty();
        assertThat(result.diff().preservedUserConstraints())
                .contains("ORIGINAL_QUERY", "FROM_YEAR", "TO_YEAR", "RESULT_LIMIT");
        assertThat(result.diff().reason()).isNotBlank();
        assertThat(result.refinementAttempt()).isEqualTo(1);
    }

    @Test
    void shouldRejectSecondAttemptBeforeCallingTheModel() {
        SearchPlanRefinementGenerator generator = mock(SearchPlanRefinementGenerator.class);
        SearchPlanRefiner refiner = refiner(generator, mock(
                SearchPlanRefinementDraftValidationPipeline.class
        ), planPipeline());

        assertRejected(
                () -> refiner.refine(context(1)),
                PlanRefinementRejectionReason.REFINEMENT_LIMIT_REACHED
        );
        verify(generator, never()).generate(context(1));
    }

    @Test
    void shouldRejectEmptyDuplicateOversizedAndOverlongSuggestions() {
        SearchPlanRefiner refiner = refiner(planPipeline());
        SearchPlanRefinementContext context = context(0);

        assertRejected(
                () -> refiner.refine(
                        context,
                        new SearchPlanRefinementDraft(
                                List.of(" ", "MAMBA"),
                                List.of(),
                                List.of("remote sensing change detection"),
                                "No effective expansion"
                        )
                ),
                PlanRefinementRejectionReason.EMPTY_SUGGESTION
        );
        SearchPlanRefinementResult bounded = refiner.refine(
                context,
                new SearchPlanRefinementDraft(
                        List.of("one", "two", "three", "four", "five", "six"),
                        List.of(),
                        List.of(),
                        "Bounded additions"
                )
        );
        assertThat(bounded.diff().addedKeywords())
                .containsExactly("one", "two", "three", "four", "five");
        assertRejected(
                () -> refiner.refine(
                        context,
                        new SearchPlanRefinementDraft(
                                List.of("x".repeat(101)),
                                List.of(),
                                List.of(),
                                "Too long"
                        )
                ),
                PlanRefinementRejectionReason.KEYWORD_TOO_LONG
        );
    }

    @Test
    void shouldRejectUnknownModelFieldsBeforeMerging() {
        String raw = """
                {
                  "synonyms": ["state space model"],
                  "abbreviations": [],
                  "conceptCombinations": [],
                  "reason": "broaden terminology",
                  "fromYear": 1900
                }
                """;
        SearchPlanRefiner refiner = refiner(
                ignored -> raw,
                refinementDraftPipeline(),
                planPipeline()
        );

        assertRejected(
                () -> refiner.refine(context(0)),
                PlanRefinementRejectionReason.INVALID_MODEL_OUTPUT
        );
    }

    @Test
    void shouldInvokeTheCompletePlanPipelineForTheMergedDraft() throws Exception {
        SearchPlanRefinementContext context = context(0);
        SearchPlanValidationPipeline pipeline = mock(SearchPlanValidationPipeline.class);
        SearchPlanValidationResult trusted = context.current().validationResult();
        when(pipeline.revalidate(
                same(context.current().generationContext()),
                anyString(),
                same(trusted)
        )).thenReturn(trusted);

        refiner(pipeline).refine(
                context,
                new SearchPlanRefinementDraft(
                        List.of("state space model"),
                        List.of(),
                        List.of(),
                        "broaden terminology"
                )
        );

        verify(pipeline).revalidate(
                same(context.current().generationContext()),
                anyString(),
                same(trusted)
        );
    }

    @Test
    void shouldMakeNoExternalToolCallWhenRevalidationFails() {
        SearchPlanRefinementContext context = context(0);
        SearchPlanValidationPipeline pipeline = mock(SearchPlanValidationPipeline.class);
        OpenAlexSearchPort openAlexSearchPort = mock(OpenAlexSearchPort.class);
        when(pipeline.revalidate(
                same(context.current().generationContext()),
                anyString(),
                same(context.current().validationResult())
        )).thenThrow(new SearchPlanValidationException(
                ValidationStage.SECURITY,
                List.of(new ValidationIssue(
                        "SECURITY_VALIDATION_FAILED",
                        "$.searchQuery",
                        "unsafe",
                        false
                ))
        ));

        assertRejected(
                () -> refiner(pipeline).refine(
                        context,
                        new SearchPlanRefinementDraft(
                                List.of("https://unsafe.example"),
                                List.of(),
                                List.of(),
                                "unsafe proposal"
                        )
                ),
                PlanRefinementRejectionReason.REFINED_PLAN_VALIDATION_FAILED
        );
        verifyNoInteractions(openAlexSearchPort);
    }

    @Test
    void shouldUseConfiguredPlanAdjustmentBudgetAsTheRefinementLimit() {
        AgentBudgetProperties budgets = new AgentBudgetProperties();
        budgets.setMaxPlanAdjustments(2);
        SearchPlanRefiner refiner = refiner(
                ignored -> {
                    throw new AssertionError("model must not be called by direct-draft tests");
                },
                mock(SearchPlanRefinementDraftValidationPipeline.class),
                planPipeline(),
                budgets
        );

        SearchPlanRefinementResult result = refiner.refine(
                context(1),
                new SearchPlanRefinementDraft(
                        List.of("state space model"), List.of(), List.of(), "Second configured attempt"
                )
        );

        assertThat(result.refinementAttempt()).isEqualTo(2);
    }

    @Test
    void shouldExposeConfiguredAggregateSuggestionLimitInThePrompt() {
        AgentBudgetProperties budgets = new AgentBudgetProperties();
        budgets.setMaxRefinementKeywords(3);

        String prompt = new SearchPlanRefinementPromptBuilder(budgets).build(context(0));

        assertThat(prompt).contains("no more than 3 total new suggestions");
    }

    private SearchPlanRefiner refiner(SearchPlanValidationPipeline pipeline) {
        return refiner(
                ignored -> {
                    throw new AssertionError("model must not be called by direct-draft tests");
                },
                mock(SearchPlanRefinementDraftValidationPipeline.class),
                pipeline
        );
    }

    private SearchPlanRefiner refiner(
            SearchPlanRefinementGenerator generator,
            SearchPlanRefinementDraftValidationPipeline draftPipeline,
            SearchPlanValidationPipeline planPipeline
    ) {
        return new SearchPlanRefiner(
                generator,
                draftPipeline,
                planPipeline,
                mapper,
                new AgentBudgetProperties()
        );
    }

    private SearchPlanRefiner refiner(
            SearchPlanRefinementGenerator generator,
            SearchPlanRefinementDraftValidationPipeline draftPipeline,
            SearchPlanValidationPipeline planPipeline,
            AgentBudgetProperties budgets
    ) {
        return new SearchPlanRefiner(
                generator,
                draftPipeline,
                planPipeline,
                mapper,
                budgets
        );
    }

    private SearchPlanRefinementDraftValidationPipeline refinementDraftPipeline() {
        return new SearchPlanRefinementDraftValidationPipeline(
                new JsonSyntaxValidator(mapper, aiProperties),
                new SearchPlanRefinementSchemaValidator(),
                new SearchPlanRefinementDraftMapper(mapper)
        );
    }

    private SearchPlanValidationPipeline planPipeline() {
        return new SearchPlanValidationPipeline(
                new JsonSyntaxValidator(mapper, aiProperties),
                new SearchPlanSchemaValidator(aiProperties),
                new SearchPlanDraftMapper(mapper),
                new SearchPlanBusinessValidator(searchProperties),
                new SearchPlanSecurityValidator(searchProperties)
        );
    }

    private SearchPlanRefinementContext context(int refinementCount) {
        SearchRequest request = new SearchRequest(
                "mamba remote sensing change detection",
                2020,
                2026,
                5
        );
        SearchPlanGenerationContext generationContext = new SearchPlanGenerationContext(
                UUID.fromString("00000000-0000-0000-0000-000000000030"),
                request,
                Instant.parse("2026-07-30T00:00:00Z"),
                2026
        );
        SearchPlanDraft initialDraft = new SearchPlanDraft(
                "Mamba-based remote sensing change detection",
                List.of("mamba", "remote sensing change detection"),
                "mamba remote sensing change detection",
                List.of("en"),
                List.of("article"),
                "newest",
                null,
                2019,
                2025,
                10
        );
        SearchPlanValidationResult current =
                new SearchPlanBusinessValidator(searchProperties)
                        .validateWithOrigins(generationContext, initialDraft);
        return new SearchPlanRefinementContext(
                new ValidatedSearchPlanContext(generationContext, current),
                refinementCount,
                20,
                2,
                "INSUFFICIENT_VERIFIED_RESULTS"
        );
    }

    private void assertFrozenFields(SearchPlan refined, SearchPlan current) {
        assertThat(refined.originalQuery()).isEqualTo(current.originalQuery());
        assertThat(refined.topic()).isEqualTo(current.topic());
        assertThat(refined.fromYear()).isEqualTo(current.fromYear());
        assertThat(refined.toYear()).isEqualTo(current.toYear());
        assertThat(refined.languages()).isEqualTo(current.languages());
        assertThat(refined.publicationTypes()).isEqualTo(current.publicationTypes());
        assertThat(refined.sort()).isEqualTo(current.sort());
        assertThat(refined.resultLimit()).isEqualTo(current.resultLimit());
        assertThat(refined.candidateLimit()).isEqualTo(current.candidateLimit());
    }

    private void assertRejected(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            PlanRefinementRejectionReason reason
    ) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        PlanRefinementRejectedException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo(reason)
                );
    }
}
