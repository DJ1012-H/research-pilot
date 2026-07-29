package com.dj1012h.researchpilot.literature.agent;

/** Produces one untrusted refinement JSON object after REFINE_PLAN is chosen. */
public interface SearchPlanRefinementGenerator {
    String generate(SearchPlanRefinementContext context);
}
