package com.dj1012h.researchpilot.literature.agent;

/** Estimated upper bound checked before an action can reach an external tool. */
public record ActionCost(int uniqueCandidates, int crossrefCalls) {
    public ActionCost {
        if (uniqueCandidates < 0 || crossrefCalls < 0) {
            throw new IllegalArgumentException("action costs must not be negative");
        }
    }
    public static ActionCost none() { return new ActionCost(0, 0); }
}
