package com.dj1012h.researchpilot.literature.normalization;

import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import org.springframework.stereotype.Component;

import java.util.List;

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
}
