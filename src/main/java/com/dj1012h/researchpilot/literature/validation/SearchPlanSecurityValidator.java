package com.dj1012h.researchpilot.literature.validation;

import com.dj1012h.researchpilot.config.LiteratureSearchProperties;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class SearchPlanSecurityValidator {

    private static final Pattern EXECUTION_SYNTAX = Pattern.compile(
            "(?i)(https?://|(?:^|[?&])(filter|sort|api[_-]?key)=|authorization\\s*:)"
    );

    private final LiteratureSearchProperties properties;

    public SearchPlanSecurityValidator(LiteratureSearchProperties properties) {
        this.properties = properties;
    }

    public SearchPlan validate(SearchPlan plan) {
        Objects.requireNonNull(plan, "plan 不能为空");

        rejectControlCharacters(plan.originalQuery(), "$.originalQuery");
        rejectControlCharacters(plan.topic(), "$.topic");
        rejectControlCharacters(plan.searchQuery(), "$.searchQuery");
        for (String keyword : plan.englishKeywords()) {
            rejectControlCharacters(keyword, "$.englishKeywords");
        }

        if (EXECUTION_SYNTAX.matcher(plan.searchQuery()).find()) {
            throw failure(
                    "SECURITY_VALIDATION_FAILED",
                    "$.searchQuery",
                    "检索式不能包含 URL、Header 或原始执行参数"
            );
        }
        if (plan.searchQuery().length() > SearchPlan.MAX_SEARCH_QUERY_LENGTH) {
            throw failure(
                    "SEARCH_QUERY_TOO_LONG",
                    "$.searchQuery",
                    "检索式超过执行长度上限"
            );
        }
        if (plan.candidateLimit() > properties.getMaxCandidateLimit()) {
            throw failure(
                    "SEARCH_REQUEST_BUDGET_EXCEEDED",
                    "$.candidateLimit",
                    "候选请求预算超过上限"
            );
        }
        return plan;
    }

    private void rejectControlCharacters(String value, String path) {
        boolean containsControlCharacter = value.codePoints()
                .anyMatch(Character::isISOControl);
        if (containsControlCharacter) {
            throw failure(
                    "SECURITY_VALIDATION_FAILED",
                    path,
                    "字段包含危险控制字符"
            );
        }
    }

    private SearchPlanValidationException failure(String code, String path, String message) {
        return new SearchPlanValidationException(
                ValidationStage.SECURITY,
                List.of(new ValidationIssue(code, path, message, false))
        );
    }
}
