package com.dj1012h.researchpilot.integration.cache;

import com.dj1012h.researchpilot.integration.crossref.CrossrefBibliographicQuery;
import com.dj1012h.researchpilot.integration.crossref.CrossrefProperties;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Builds versioned opaque keys; request text and credentials never appear in a Redis key. */
@Component
public class CacheKeyFactory {

    private static final String OPENALEX_BEHAVIOR_VERSION = "newest-relevance-pool-v2";
    private static final String CROSSREF_METADATA_VERSION = "abstract-enrichment-v1";

    private final LiteratureCacheProperties properties;
    private final DoiNormalizer doiNormalizer;
    private final OpenAlexProperties openAlexProperties;
    private final CrossrefProperties crossrefProperties;

    public CacheKeyFactory(
            LiteratureCacheProperties properties,
            DoiNormalizer doiNormalizer,
            OpenAlexProperties openAlexProperties,
            CrossrefProperties crossrefProperties
    ) {
        this.properties = properties;
        this.doiNormalizer = doiNormalizer;
        this.openAlexProperties = openAlexProperties;
        this.crossrefProperties = crossrefProperties;
    }

    public String openAlexSearch(OpenAlexQuery query) {
        String canonical = String.join("\n",
                "behavior=" + OPENALEX_BEHAVIOR_VERSION,
                "search=" + text(query.search()),
                "from=" + query.fromPublicationDate(),
                "to=" + query.toPublicationDate(),
                "workTypes=" + canonicalList(query.workTypes()),
                "languages=" + canonicalList(query.languages()),
                "sort=" + query.sort().name(),
                "perPage=" + query.pageSizeOrDefault(openAlexProperties.getDefaultPageSize()));
        return key("openalex", "search", canonical);
    }

    public Optional<String> crossrefDoi(String doi) {
        String normalized = doiNormalizer.normalize(doi);
        return normalized == null
                ? Optional.empty()
                : Optional.of(key("crossref", "doi", "metadata=" + CROSSREF_METADATA_VERSION + "\ndoi=" + normalized));
    }

    public String crossrefBibliographic(CrossrefBibliographicQuery query) {
        String canonical = String.join("\n",
                "metadata=" + CROSSREF_METADATA_VERSION,
                "title=" + text(query.title()),
                "firstAuthor=" + text(query.firstAuthor()),
                "publicationYear=" + (query.publicationYear() == null ? "" : query.publicationYear()),
                "sourceName=" + text(query.sourceName()),
                "rows=" + crossrefProperties.getBibliographicRows());
        return key("crossref", "bibliographic", canonical);
    }

    private String key(String provider, String operation, String canonical) {
        return properties.getKeyPrefix() + ":" + provider + ":" + operation + ":" + sha256(canonical);
    }

    private String canonicalList(List<String> values) {
        return values.stream().map(this::text).distinct().sorted().reduce((a, b) -> a + "," + b).orElse("");
    }

    private String text(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
