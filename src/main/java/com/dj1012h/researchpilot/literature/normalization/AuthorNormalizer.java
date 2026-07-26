package com.dj1012h.researchpilot.literature.normalization;

import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Conservative author normalization that preserves name order and punctuation. */
@Component
public class AuthorNormalizer {

    public String normalize(String rawAuthor) {
        return UnicodeTextNormalizer.normalize(rawAuthor);
    }

    public String normalizeFirstAuthor(List<CandidatePaper.Author> authors) {
        if (authors == null) {
            return null;
        }
        for (CandidatePaper.Author author : authors) {
            if (author == null) {
                continue;
            }
            String normalized = normalize(author.displayName());
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    /**
     * Produces a conservative first-author comparison key from surname and
     * given-name initials. This is for field evidence only; it does not alter
     * the order-preserving normalization used by candidate deduplication.
     */
    public String normalizeForComparison(String rawAuthor) {
        String normalized = normalize(rawAuthor);
        if (normalized == null) {
            return null;
        }

        String punctuationFree = normalized.replaceAll("[^\\p{L}\\p{N},\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (punctuationFree.isEmpty()) {
            return null;
        }

        String[] commaParts = punctuationFree.split(",", 2);
        if (commaParts.length == 2 && !commaParts[0].isBlank() && !commaParts[1].isBlank()) {
            return comparisonKey(commaParts[0].trim(), commaParts[1].trim().split("\\s+"));
        }

        String[] parts = punctuationFree.split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        String surname = parts[parts.length - 1];
        StringBuilder initials = new StringBuilder();
        for (int index = 0; index < parts.length - 1; index++) {
            if (!parts[index].isEmpty()) {
                initials.append(parts[index].codePointAt(0));
            }
        }
        return surname + "|" + initials.toString().toLowerCase(Locale.ROOT);
    }

    private String comparisonKey(String surname, String[] givenNames) {
        StringBuilder initials = new StringBuilder();
        for (String givenName : givenNames) {
            if (!givenName.isEmpty()) {
                initials.append(givenName.codePointAt(0));
            }
        }
        return surname + "|" + initials.toString().toLowerCase(Locale.ROOT);
    }
}
