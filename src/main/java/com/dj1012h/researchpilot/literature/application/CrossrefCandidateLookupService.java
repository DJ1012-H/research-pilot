package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.config.LiteratureSearchProperties;
import com.dj1012h.researchpilot.integration.crossref.CrossrefApiException;
import com.dj1012h.researchpilot.integration.crossref.CrossrefBibliographicLookupResult;
import com.dj1012h.researchpilot.integration.crossref.CrossrefBibliographicQuery;
import com.dj1012h.researchpilot.integration.crossref.CrossrefFailureType;
import com.dj1012h.researchpilot.integration.crossref.CrossrefLookupResult;
import com.dj1012h.researchpilot.integration.crossref.CrossrefProperties;
import com.dj1012h.researchpilot.integration.crossref.CrossrefSearchPort;
import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CrossrefCandidateLookupService {

    private final CrossrefSearchPort crossrefSearchPort;
    private final CrossrefProperties crossrefProperties;
    private final LiteratureSearchProperties searchProperties;
    private final DoiNormalizer doiNormalizer;
    private final CrossrefTitleQueryGuard titleQueryGuard;

    /** Compatibility constructor for direct callers from the DOI-only stage. */
    public CrossrefCandidateLookupService(
            CrossrefSearchPort crossrefSearchPort,
            CrossrefProperties crossrefProperties,
            LiteratureSearchProperties searchProperties
    ) {
        this(crossrefSearchPort, crossrefProperties, searchProperties,
                new DoiNormalizer(), new CrossrefTitleQueryGuard());
    }

    @Autowired
    public CrossrefCandidateLookupService(
            CrossrefSearchPort crossrefSearchPort,
            CrossrefProperties crossrefProperties,
            LiteratureSearchProperties searchProperties,
            DoiNormalizer doiNormalizer,
            CrossrefTitleQueryGuard titleQueryGuard
    ) {
        this.crossrefSearchPort = crossrefSearchPort;
        this.crossrefProperties = crossrefProperties;
        this.searchProperties = searchProperties;
        this.doiNormalizer = doiNormalizer;
        this.titleQueryGuard = titleQueryGuard;
    }

    public CrossrefLookupSummary lookup(List<CandidatePaper> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        LookupTargets targets = targets(candidates);
        if (!crossrefProperties.isEnabled()) {
            // A disabled source performs no port, gate, retry, or HTTP operation.
            return summary(targets, 0, 0, 0, 0, 0, false, false, List.of(), List.of());
        }

        int attempted = 0;
        int found = 0;
        int notFound = 0;
        int failed = 0;
        int skipped = 0;
        boolean sourceAvailable = true;
        List<CrossrefWorkMetadata> metadata = new ArrayList<>();
        List<CrossrefBibliographicLookupResult> bibliographicResults = new ArrayList<>();
        List<LookupTarget> ordered = targets.ordered();
        int budget = searchProperties.getMaxCrossrefLookupsPerRequest();

        for (int index = 0; index < ordered.size(); index++) {
            if (attempted >= budget) {
                skipped += ordered.size() - index;
                break;
            }
            attempted++;
            try {
                LookupOutcome outcome = execute(ordered.get(index));
                if (outcome.found()) {
                    found++;
                    metadata.addAll(outcome.metadata());
                } else {
                    notFound++;
                }
                if (outcome.bibliographicResult() != null) {
                    bibliographicResults.add(outcome.bibliographicResult());
                }
            } catch (CrossrefApiException exception) {
                if (isConfigurationFailure(exception)) throw exception;
                failed++;
                if (isSourceUnavailable(exception)) {
                    sourceAvailable = false;
                    break;
                }
            }
        }
        return summary(targets, attempted, found, notFound, failed, skipped, true, sourceAvailable,
                metadata, bibliographicResults);
    }

    private LookupTargets targets(List<CandidatePaper> candidates) {
        // Keep valid DOI values stable and deduplicated before any external request.
        LinkedHashSet<String> dois = new LinkedHashSet<>();
        Map<String, CrossrefBibliographicQuery> queries = new LinkedHashMap<>();
        for (CandidatePaper candidate : candidates) {
            if (candidate == null) continue;
            String doi = doiNormalizer.normalize(candidate.doi());
            if (doi != null) {
                // Valid DOIs always use the exact DOI route; no title fallback is added.
                dois.add(doi);
                continue;
            }
            CrossrefTitleQueryGuard.Decision title = titleQueryGuard.assess(candidate.title());
            if (!title.allowed()) continue;
            CrossrefBibliographicQuery query = new CrossrefBibliographicQuery(
                    title.normalizedTitle(), firstAuthor(candidate), candidate.publicationYear(), candidate.sourceName());
            queries.putIfAbsent(query.deduplicationKey(), query);
        }
        return new LookupTargets(List.copyOf(dois), List.copyOf(queries.values()));
    }

    private String firstAuthor(CandidatePaper candidate) {
        return candidate.authors().stream()
                .map(CandidatePaper.Author::displayName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private LookupOutcome execute(LookupTarget target) {
        if (target.doi() != null) {
            CrossrefLookupResult result = crossrefSearchPort.findByDoi(target.doi());
            return result.status() == CrossrefLookupResult.Status.FOUND
                    ? LookupOutcome.found(List.of(result.metadata()), null)
                    : LookupOutcome.notFound(null);
        }
        CrossrefBibliographicLookupResult result = crossrefSearchPort.findByBibliographic(target.query());
        return result.status() == CrossrefBibliographicLookupResult.Status.NOT_FOUND
                ? LookupOutcome.notFound(result)
                : LookupOutcome.found(result.candidates(), result);
    }

    private boolean isConfigurationFailure(CrossrefApiException exception) {
        return exception.getFailureType() == CrossrefFailureType.MAILTO_MISSING
                || exception.getFailureType() == CrossrefFailureType.USER_AGENT_MISSING;
    }

    private boolean isSourceUnavailable(CrossrefApiException exception) {
        return switch (exception.getFailureType()) {
            case RATE_LIMITED, SERVER_ERROR, TIMEOUT, TRANSPORT_ERROR, INTERRUPTED -> true;
            default -> false;
        };
    }

    private CrossrefLookupSummary summary(
            LookupTargets targets, int attempted, int found, int notFound, int failed, int skipped,
            boolean enabled, boolean available, List<CrossrefWorkMetadata> metadata,
            List<CrossrefBibliographicLookupResult> bibliographicResults
    ) {
        return new CrossrefLookupSummary(targets.dois().size(), targets.queries().size(), attempted, found,
                notFound, failed, skipped, enabled, available, metadata, bibliographicResults);
    }

    private record LookupTargets(List<String> dois, List<CrossrefBibliographicQuery> queries) {
        List<LookupTarget> ordered() {
            List<LookupTarget> targets = new ArrayList<>();
            dois.forEach(doi -> targets.add(LookupTarget.doi(doi)));
            queries.forEach(query -> targets.add(LookupTarget.query(query)));
            return targets;
        }
    }

    private record LookupTarget(String doi, CrossrefBibliographicQuery query) {
        static LookupTarget doi(String doi) { return new LookupTarget(doi, null); }
        static LookupTarget query(CrossrefBibliographicQuery query) { return new LookupTarget(null, query); }
    }

    private record LookupOutcome(
            boolean found,
            List<CrossrefWorkMetadata> metadata,
            CrossrefBibliographicLookupResult bibliographicResult
    ) {
        static LookupOutcome found(List<CrossrefWorkMetadata> metadata, CrossrefBibliographicLookupResult result) {
            return new LookupOutcome(true, metadata, result);
        }
        static LookupOutcome notFound(CrossrefBibliographicLookupResult result) {
            return new LookupOutcome(false, List.of(), result);
        }
    }
}
