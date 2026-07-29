package com.dj1012h.researchpilot.literature.review;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses only the canonical P + positive base-10 integer form. */
@Component
public class CitationIdParser {

    private static final Pattern PATTERN = Pattern.compile("^P([1-9][0-9]*)$");

    public CitationId parse(String rawValue, String jsonPath) {
        if (rawValue == null) {
            throw malformed(jsonPath);
        }
        Matcher matcher = PATTERN.matcher(rawValue);
        if (!matcher.matches()) {
            throw malformed(jsonPath);
        }
        try {
            return new CitationId(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException exception) {
            throw malformed(jsonPath);
        }
    }

    private ReviewDraftValidationException malformed(String jsonPath) {
        return new ReviewDraftValidationException(
                ReviewValidationStage.CITATION_GUARD,
                java.util.List.of(new ReviewValidationIssue(
                        "MALFORMED_CITATION_ID", jsonPath, true))
        );
    }
}
