package com.dj1012h.researchpilot.literature.normalization;

import org.springframework.stereotype.Component;

/** Conservative venue normalization without abbreviation expansion or source aliases. */
@Component
public class VenueNormalizer {

    public String normalize(String rawVenue) {
        return UnicodeTextNormalizer.normalize(rawVenue);
    }
}
