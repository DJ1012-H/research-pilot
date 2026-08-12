package com.dj1012h.researchpilot.literature.rag.answer;

public class RagAnswerPromptBudgetException extends RuntimeException {
    public RagAnswerPromptBudgetException(String code) {
        super(code);
    }
}
