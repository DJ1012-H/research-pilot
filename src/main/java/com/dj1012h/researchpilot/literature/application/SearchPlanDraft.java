package com.dj1012h.researchpilot.literature.application;

import java.util.List;
import java.util.Objects;

/**
 * Strictly mapped but not yet trusted model output.
 *
 * <p>This type contains only search intent. It deliberately excludes the
 * original request, candidate limits and every transport or credential field.</p>
 */
public record SearchPlanDraft(
        String topic,
        List<String> englishKeywords,
        String searchQuery,
        List<String> languages,
        List<String> publicationTypes,
        String sort,
        Integer recentYears,
        Integer fromYear,
        Integer toYear,
        Integer resultLimit
) {

    public SearchPlanDraft {
        englishKeywords = copyList(englishKeywords, "englishKeywords");
        languages = copyList(languages, "languages");
        publicationTypes = copyList(publicationTypes, "publicationTypes");
    }

    private static List<String> copyList(List<String> values, String field) {
        return List.copyOf(Objects.requireNonNull(values, field + " 不能为空"));
    }
}
