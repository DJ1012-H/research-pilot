package com.dj1012h.researchpilot.literature.review;

import java.util.List;
import java.util.Objects;

/** Strictly mapped but still untrusted statement proposed by the model. */
public record ReviewStatement(
        ReviewStatementType type,
        String text,
        List<String> citationIds
) {
    public ReviewStatement {
        type = Objects.requireNonNull(type, "type must not be null");
        text = Objects.requireNonNull(text, "text must not be null");
        citationIds = List.copyOf(Objects.requireNonNull(citationIds, "citationIds must not be null"));
    }
}
