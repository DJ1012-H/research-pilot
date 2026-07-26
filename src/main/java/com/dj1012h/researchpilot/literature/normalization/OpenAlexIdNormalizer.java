package com.dj1012h.researchpilot.literature.normalization;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalizes only OpenAlex Work identifiers; it is not a generic identifier parser. */
@Component
public class OpenAlexIdNormalizer {

    private static final Pattern URL_PREFIX = Pattern.compile("(?i)^https?://openalex\\.org/");
    private static final Pattern WORK_ID = Pattern.compile("(?i)^W\\d+$");

    public String normalize(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        String value = Normalizer.normalize(rawId, Normalizer.Form.NFKC).trim();
        value = URL_PREFIX.matcher(value).replaceFirst("");
        if (!WORK_ID.matcher(value).matches()) {
            return null;
        }
        return value.toUpperCase(Locale.ROOT);
    }
}
