package com.dj1012h.researchpilot.literature.agent;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchActionSecurityValidatorTest {

    @Test
    void shouldRequireTheCurrentJavaComputedAllowedSet() {
        SearchActionSecurityValidator validator = new SearchActionSecurityValidator();
        assertThat(validator.validate(AgentAction.COMPLETE, Set.of(AgentAction.REFINE_PLAN, AgentAction.COMPLETE)))
                .isEqualTo(AgentAction.COMPLETE);
        assertThatThrownBy(() -> validator.validate(AgentAction.VERIFY_WITH_CROSSREF,
                Set.of(AgentAction.REFINE_PLAN, AgentAction.COMPLETE)))
                .isInstanceOfSatisfying(SearchActionValidationException.class, exception ->
                        assertThat(exception.getIssues()).extracting(SearchActionValidationIssue::code)
                                .containsExactly("ACTION_NOT_ALLOWED"));
    }
}
