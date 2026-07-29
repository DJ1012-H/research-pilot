package com.dj1012h.researchpilot.literature.agent;

import com.dj1012h.researchpilot.config.AgentBudgetProperties;
import com.dj1012h.researchpilot.config.StructuredOutputMapper;
import com.dj1012h.researchpilot.literature.application.SearchPlanDraft;
import com.dj1012h.researchpilot.literature.model.ConstraintOrigin;
import com.dj1012h.researchpilot.literature.model.SearchConstraintOrigins;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchPlanValidationResult;
import com.dj1012h.researchpilot.literature.validation.SearchPlanValidationException;
import com.dj1012h.researchpilot.literature.validation.SearchPlanValidationPipeline;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Applies one model-proposed, append-only search-expression refinement.
 *
 * <p>The component neither decides whether refinement is appropriate nor calls
 * a literature provider. Every merged draft re-enters the complete trusted
 * search-plan validation pipeline.</p>
 */
@Component
public class SearchPlanRefiner {

    private static final int MAX_REFINEMENT_COUNT = 1;

    private final SearchPlanRefinementGenerator generator;
    private final SearchPlanRefinementDraftValidationPipeline refinementDraftPipeline;
    private final SearchPlanValidationPipeline searchPlanValidationPipeline;
    private final StructuredOutputMapper structuredOutputMapper;
    private final AgentBudgetProperties properties;

    public SearchPlanRefiner(
            SearchPlanRefinementGenerator generator,
            SearchPlanRefinementDraftValidationPipeline refinementDraftPipeline,
            SearchPlanValidationPipeline searchPlanValidationPipeline,
            StructuredOutputMapper structuredOutputMapper,
            AgentBudgetProperties properties
    ) {
        this.generator = Objects.requireNonNull(generator, "generator must not be null");
        this.refinementDraftPipeline = Objects.requireNonNull(
                refinementDraftPipeline,
                "refinementDraftPipeline must not be null"
        );
        this.searchPlanValidationPipeline = Objects.requireNonNull(
                searchPlanValidationPipeline,
                "searchPlanValidationPipeline must not be null"
        );
        this.structuredOutputMapper = Objects.requireNonNull(
                structuredOutputMapper,
                "structuredOutputMapper must not be null"
        );
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public SearchPlanRefinementResult refine(SearchPlanRefinementContext context) {
        requireEligibleContext(context);
        String rawOutput = generator.generate(context);
        SearchPlanRefinementDraft draft;
        try {
            draft = refinementDraftPipeline.validate(rawOutput);
        } catch (SearchPlanValidationException exception) {
            throw new PlanRefinementRejectedException(
                    PlanRefinementRejectionReason.INVALID_MODEL_OUTPUT,
                    exception
            );
        }
        return refine(context, draft);
    }

    SearchPlanRefinementResult refine(
            SearchPlanRefinementContext context,
            SearchPlanRefinementDraft draft
    ) {
        requireEligibleContext(context);
        Objects.requireNonNull(draft, "draft must not be null");

        SearchPlanValidationResult current = context.current().validationResult();
        SearchPlan currentPlan = current.plan();
        String reason = sanitizeReason(draft.reason());
        List<String> additions = sanitizeAdditions(currentPlan, draft);
        List<String> combinedKeywords = append(currentPlan.englishKeywords(), additions);
        String refinedSearchQuery = refinedSearchQuery(currentPlan.searchQuery(), additions);
        SearchPlanDraft mergedDraft = new SearchPlanDraft(
                currentPlan.topic(),
                combinedKeywords,
                refinedSearchQuery,
                currentPlan.languages().stream().map(language -> language.apiValue()).toList(),
                currentPlan.publicationTypes(),
                currentPlan.sort().name().toLowerCase(Locale.ROOT),
                null,
                currentPlan.fromYear(),
                currentPlan.toYear(),
                currentPlan.resultLimit()
        );

        SearchPlanValidationResult revalidated;
        try {
            String mergedJson = structuredOutputMapper.writeValueAsString(mergedDraft);
            revalidated = searchPlanValidationPipeline.revalidate(
                    context.current().generationContext(),
                    mergedJson,
                    current
            );
        } catch (JsonProcessingException | SearchPlanValidationException exception) {
            throw new PlanRefinementRejectedException(
                    PlanRefinementRejectionReason.REFINED_PLAN_VALIDATION_FAILED,
                    exception
            );
        }

        SearchPlanDiff diff = new SearchPlanDiff(
                additions,
                List.of(),
                preservedUserConstraints(current.origins()),
                reason
        );
        return new SearchPlanRefinementResult(
                revalidated.plan(),
                revalidated.origins(),
                diff,
                context.refinementCount() + 1
        );
    }

    private void requireEligibleContext(SearchPlanRefinementContext context) {
        Objects.requireNonNull(context, "context must not be null");
        if (context.refinementCount() >= MAX_REFINEMENT_COUNT) {
            throw new PlanRefinementRejectedException(
                    PlanRefinementRejectionReason.REFINEMENT_LIMIT_REACHED
            );
        }
    }

    private String sanitizeReason(String value) {
        if (value == null || value.isBlank() || containsControlCharacter(value)) {
            throw new PlanRefinementRejectedException(
                    PlanRefinementRejectionReason.REASON_INVALID
            );
        }
        String normalized = normalizeWhitespace(value);
        if (normalized.length() > properties.getMaxRefinementReasonLength()) {
            throw new PlanRefinementRejectedException(
                    PlanRefinementRejectionReason.REASON_INVALID
            );
        }
        return normalized;
    }

    private List<String> sanitizeAdditions(
            SearchPlan currentPlan,
            SearchPlanRefinementDraft draft
    ) {
        Map<String, String> existing = new LinkedHashMap<>();
        currentPlan.englishKeywords().forEach(
                keyword -> existing.put(keyword.toLowerCase(Locale.ROOT), keyword)
        );
        Map<String, String> additions = new LinkedHashMap<>();
        addSuggestions(additions, existing, draft.synonyms());
        addSuggestions(additions, existing, draft.abbreviations());
        addSuggestions(additions, existing, draft.conceptCombinations());

        if (additions.isEmpty()) {
            throw new PlanRefinementRejectedException(
                    PlanRefinementRejectionReason.EMPTY_SUGGESTION
            );
        }
        if (additions.size() > properties.getMaxRefinementKeywords()
                || currentPlan.englishKeywords().size() + additions.size()
                > SearchPlan.MAX_KEYWORD_COUNT) {
            throw new PlanRefinementRejectedException(
                    PlanRefinementRejectionReason.TOO_MANY_KEYWORDS
            );
        }
        return List.copyOf(additions.values());
    }

    private void addSuggestions(
            Map<String, String> additions,
            Map<String, String> existing,
            List<String> suggestions
    ) {
        for (String suggestion : suggestions) {
            if (suggestion == null || containsControlCharacter(suggestion)) {
                throw new PlanRefinementRejectedException(
                        PlanRefinementRejectionReason.KEYWORD_TOO_LONG
                );
            }
            String normalized = normalizeWhitespace(suggestion);
            if (normalized.isBlank()) {
                continue;
            }
            if (normalized.length() > properties.getMaxRefinementKeywordLength()) {
                throw new PlanRefinementRejectedException(
                        PlanRefinementRejectionReason.KEYWORD_TOO_LONG
                );
            }
            String key = normalized.toLowerCase(Locale.ROOT);
            if (!existing.containsKey(key)) {
                additions.putIfAbsent(key, normalized);
            }
        }
    }

    private String refinedSearchQuery(String currentQuery, List<String> additions) {
        String query = currentQuery + " OR " + String.join(" OR ", additions);
        if (query.length() > SearchPlan.MAX_SEARCH_QUERY_LENGTH) {
            throw new PlanRefinementRejectedException(
                    PlanRefinementRejectionReason.REFINED_QUERY_TOO_LONG
            );
        }
        return query;
    }

    private List<String> preservedUserConstraints(SearchConstraintOrigins origins) {
        return origins.asMap().entrySet().stream()
                .filter(entry -> entry.getValue() == ConstraintOrigin.USER_EXPLICIT)
                .map(entry -> entry.getKey().name())
                .toList();
    }

    private List<String> append(List<String> current, List<String> additions) {
        List<String> combined = new ArrayList<>(current);
        combined.addAll(additions);
        return List.copyOf(combined);
    }

    private String normalizeWhitespace(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
