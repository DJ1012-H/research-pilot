package com.dj1012h.researchpilot.literature.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchActionBusinessValidatorTest {

    @Test
    void shouldMapOnlyTheExistingModelSelectableAgentActions() {
        SearchActionBusinessValidator validator = new SearchActionBusinessValidator();
        assertThat(validator.validate(new SearchActionDraft("EVALUATE_RESULTS")))
                .isEqualTo(AgentAction.EVALUATE_RESULTS);
        assertThatThrownBy(() -> validator.validate(new SearchActionDraft("TERMINATE")))
                .isInstanceOf(SearchActionValidationException.class);
    }
}
