package com.dj1012h.researchpilot.literature.normalization;

import org.springframework.stereotype.Component;

/** Conservative title normalization for exact identity keys and later evidence. */
@Component
public class TitleNormalizer {

    public String normalize(String rawTitle) {
        return UnicodeTextNormalizer.normalize(rawTitle);
    }
}
