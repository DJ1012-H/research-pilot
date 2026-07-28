package com.dj1012h.researchpilot.literature.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface SearchActionAiService {

    @SystemMessage("""
            You select the next action for a bounded academic literature workflow.

            Select exactly one action from allowedActions.
            Never invent an action.
            Never modify budgets, limits, deadlines or user constraints.
            Never call a tool.
            Return only one JSON object.
            """)
    @UserMessage("{{context}}")
    String decide(@V("context") String context);
}
