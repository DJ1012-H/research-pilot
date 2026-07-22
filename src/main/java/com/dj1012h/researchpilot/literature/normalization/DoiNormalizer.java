package com.dj1012h.researchpilot.literature.normalization;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/** Normalizes syntactically recognizable DOI values without verifying registration. */
@Component
public class DoiNormalizer {

    private static final Pattern DOI_PREFIX = Pattern.compile(
            "(?i)^(?:https?://(?:dx\\.)?doi\\.org/|doi\\s*:\\s*)"
    );
    private static final Pattern DOI_SYNTAX = Pattern.compile("^10\\.\\d{4,9}/\\S+$");
    private static final String OUTER_LEADING_PUNCTUATION = "“”‘’\"'";
    private static final String OUTER_TRAILING_PUNCTUATION = "，。；“”‘’\"'）";

    public String normalize(String rawDoi) {
        String candidate = trimToNull(rawDoi);
        if (candidate == null) {
            return null;
        }

        candidate = trimToNull(DOI_PREFIX.matcher(candidate).replaceFirst(""));
        if (candidate == null) {
            return null;
        }

        candidate = stripKnownOuterPunctuation(candidate);
        candidate = stripUnmatchedTrailingParentheses(candidate);
        if (!DOI_SYNTAX.matcher(candidate).matches()) {
            return null;
        }
        return candidate.toLowerCase(Locale.ROOT);
    }

    private String stripKnownOuterPunctuation(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && OUTER_LEADING_PUNCTUATION.indexOf(value.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && OUTER_TRAILING_PUNCTUATION.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(start, end).trim();
    }

    private String stripUnmatchedTrailingParentheses(String value) {
        int balance = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '(') {
                balance++;
            } else if (character == ')') {
                balance--;
            }
        }
        int end = value.length();
        while (balance < 0 && end > 0 && value.charAt(end - 1) == ')') {
            end--;
            balance++;
        }
        return value.substring(0, end);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
