package com.dj1012h.researchpilot.literature.validation;

import com.dj1012h.researchpilot.config.LiteratureSearchProperties;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.model.SearchSort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchPlanSecurityValidatorTest {

    private final LiteratureSearchProperties properties = new LiteratureSearchProperties();
    private final SearchPlanSecurityValidator validator =
            new SearchPlanSecurityValidator(properties);

    @Test
    void shouldAcceptSafePlan() {
        SearchPlan plan = plan("Mamba remote sensing change detection", 30);

        assertThat(validator.validate(plan)).isSameAs(plan);
    }

    @Test
    void shouldRejectControlCharacterWithoutRetry() {
        assertSecurityFailure(
                plan("Mamba remote sensing\nchange detection", 30),
                "SECURITY_VALIDATION_FAILED"
        );
    }

    @Test
    void shouldRejectUrlOrRawExecutionSyntaxWithoutRetry() {
        assertSecurityFailure(
                plan("Mamba https://malicious.example?filter=all", 30),
                "SECURITY_VALIDATION_FAILED"
        );
    }

    @Test
    void shouldRejectOverlongSearchQueryAtExecutionBoundary() {
        assertSecurityFailure(
                plan("Mamba " + "a".repeat(SearchPlan.MAX_SEARCH_QUERY_LENGTH), 30),
                "SEARCH_QUERY_TOO_LONG"
        );
    }

    @Test
    void shouldRejectCandidateBudgetAboveRuntimePolicy() {
        LiteratureSearchProperties smallerBudget = new LiteratureSearchProperties();
        smallerBudget.setMaxCandidateLimit(20);
        SearchPlanSecurityValidator smallerBudgetValidator =
                new SearchPlanSecurityValidator(smallerBudget);

        assertThatThrownBy(() -> smallerBudgetValidator.validate(
                plan("Mamba remote sensing change detection", 30)
        ))
                .isInstanceOfSatisfying(SearchPlanValidationException.class, exception -> {
                    assertThat(exception.getIssues())
                            .extracting(ValidationIssue::code)
                            .containsExactly("SEARCH_REQUEST_BUDGET_EXCEEDED");
                    assertThat(exception.isRetryable()).isFalse();
                });
    }

    @Test
    void trustedPlanMustNotExposeTransportOrCredentialFields() {
        assertThat(SearchPlan.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain(
                        "url",
                        "baseUrl",
                        "endpoint",
                        "headers",
                        "authorization",
                        "apiKey",
                        "tool",
                        "rawModelOutput"
                );
    }

    private void assertSecurityFailure(SearchPlan plan, String expectedCode) {
        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOfSatisfying(SearchPlanValidationException.class, exception -> {
                    assertThat(exception.getStage()).isEqualTo(ValidationStage.SECURITY);
                    assertThat(exception.getIssues())
                            .extracting(ValidationIssue::code)
                            .containsExactly(expectedCode);
                    assertThat(exception.isRetryable()).isFalse();
                });
    }

    private SearchPlan plan(String searchQuery, int candidateLimit) {
        return new SearchPlan(
                "Mamba 遥感变化检测",
                "Mamba remote sensing change detection",
                List.of("Mamba", "remote sensing", "change detection"),
                searchQuery,
                Set.of(),
                List.of("article"),
                SearchSort.RELEVANCE,
                2020,
                2026,
                candidateLimit,
                10
        );
    }
}
