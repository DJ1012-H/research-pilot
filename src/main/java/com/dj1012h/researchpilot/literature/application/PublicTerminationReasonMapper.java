package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.literature.agent.TerminationReason;
import com.dj1012h.researchpilot.literature.api.dto.PublicTerminationReason;
import org.springframework.stereotype.Component;

/** Removes internal budget and failure detail from the public response. */
@Component
public class PublicTerminationReasonMapper {

    public PublicTerminationReason toPublic(TerminationReason reason) {
        if (reason == null) {
            return PublicTerminationReason.SAFELY_TERMINATED;
        }
        return switch (reason) {
            case TARGET_REACHED -> PublicTerminationReason.TARGET_REACHED;
            case PARTIAL_RESULTS -> PublicTerminationReason.PARTIAL_RESULTS;
            case NO_VERIFIED_RESULTS -> PublicTerminationReason.NO_VERIFIED_RESULTS;
            case SEARCH_ROUND_LIMIT_REACHED,
                    PLAN_ADJUSTMENT_LIMIT_REACHED,
                    STEP_LIMIT_REACHED,
                    CANDIDATE_BUDGET_EXHAUSTED,
                    CROSSREF_BUDGET_EXHAUSTED -> PublicTerminationReason.LIMIT_REACHED;
            case DEADLINE_EXCEEDED -> PublicTerminationReason.DEADLINE_EXCEEDED;
            case EXTERNAL_SERVICE_UNAVAILABLE -> PublicTerminationReason.EXTERNAL_SERVICE_UNAVAILABLE;
            case INVALID_STATE, UNEXPECTED_FAILURE -> PublicTerminationReason.SAFELY_TERMINATED;
        };
    }
}
