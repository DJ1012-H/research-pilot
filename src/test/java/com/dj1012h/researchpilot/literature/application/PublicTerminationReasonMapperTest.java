package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.agent.TerminationReason;
import com.dj1012h.researchpilot.literature.api.dto.PublicTerminationReason;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicTerminationReasonMapperTest {

    private final PublicTerminationReasonMapper mapper = new PublicTerminationReasonMapper();

    @Test
    void shouldCollapseInternalBudgetsAndFailuresToSafePublicValues() {
        assertThat(mapper.toPublic(TerminationReason.SEARCH_ROUND_LIMIT_REACHED))
                .isEqualTo(PublicTerminationReason.LIMIT_REACHED);
        assertThat(mapper.toPublic(TerminationReason.PLAN_ADJUSTMENT_LIMIT_REACHED))
                .isEqualTo(PublicTerminationReason.LIMIT_REACHED);
        assertThat(mapper.toPublic(TerminationReason.STEP_LIMIT_REACHED))
                .isEqualTo(PublicTerminationReason.LIMIT_REACHED);
        assertThat(mapper.toPublic(TerminationReason.CANDIDATE_BUDGET_EXHAUSTED))
                .isEqualTo(PublicTerminationReason.LIMIT_REACHED);
        assertThat(mapper.toPublic(TerminationReason.CROSSREF_BUDGET_EXHAUSTED))
                .isEqualTo(PublicTerminationReason.LIMIT_REACHED);
        assertThat(mapper.toPublic(TerminationReason.INVALID_STATE))
                .isEqualTo(PublicTerminationReason.SAFELY_TERMINATED);
        assertThat(mapper.toPublic(TerminationReason.UNEXPECTED_FAILURE))
                .isEqualTo(PublicTerminationReason.SAFELY_TERMINATED);
        assertThat(mapper.toPublic(null)).isEqualTo(PublicTerminationReason.SAFELY_TERMINATED);
    }
}
