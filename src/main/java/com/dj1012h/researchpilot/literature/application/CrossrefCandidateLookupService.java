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
import com.dj1012h.researchpilot.literature.model.CandidateDeduplicationResult;
import com.dj1012h.researchpilot.literature.model.CandidateLookupResult;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import com.dj1012h.researchpilot.literature.model.NormalizedCandidate;
import com.dj1012h.researchpilot.literature.normalization.DoiNormalizer;
import com.dj1012h.researchpilot.literature.normalization.AuthorNormalizer;
import com.dj1012h.researchpilot.literature.normalization.OpenAlexIdNormalizer;
import com.dj1012h.researchpilot.literature.normalization.TitleNormalizer;
import com.dj1012h.researchpilot.literature.normalization.VenueNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CrossrefCandidateLookupService {

    private final CrossrefSearchPort crossrefSearchPort;
    private final CrossrefProperties crossrefProperties;
    private final LiteratureSearchProperties searchProperties;
    private final CrossrefTitleQueryGuard titleQueryGuard;
    private final CandidateDeduplicationService candidateDeduplicationService;

    /** Compatibility constructor for direct callers from the DOI-only stage. */
    public CrossrefCandidateLookupService(
            CrossrefSearchPort crossrefSearchPort,
            CrossrefProperties crossrefProperties,
            LiteratureSearchProperties searchProperties
    ) {
        this(crossrefSearchPort, crossrefProperties, searchProperties,
                new DoiNormalizer(), new CrossrefTitleQueryGuard(), defaultDeduplicationService());
    }

    public CrossrefCandidateLookupService(
            CrossrefSearchPort crossrefSearchPort,
            CrossrefProperties crossrefProperties,
            LiteratureSearchProperties searchProperties,
            DoiNormalizer doiNormalizer,
            CrossrefTitleQueryGuard titleQueryGuard
    ) {
        this(crossrefSearchPort, crossrefProperties, searchProperties, doiNormalizer, titleQueryGuard,
                defaultDeduplicationService(doiNormalizer));
    }

    @Autowired
    public CrossrefCandidateLookupService(
            CrossrefSearchPort crossrefSearchPort,
            CrossrefProperties crossrefProperties,
            LiteratureSearchProperties searchProperties,
            DoiNormalizer doiNormalizer,
            CrossrefTitleQueryGuard titleQueryGuard,
            CandidateDeduplicationService candidateDeduplicationService
    ) {
        this.crossrefSearchPort = crossrefSearchPort;
        this.crossrefProperties = crossrefProperties;
        this.searchProperties = searchProperties;
        this.titleQueryGuard = titleQueryGuard;
        this.candidateDeduplicationService = candidateDeduplicationService;
    }

    public CrossrefLookupSummary lookup(List<CandidatePaper> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        return lookup(candidateDeduplicationService.deduplicate(candidates));
    }

    public CrossrefLookupSummary lookup(CandidateDeduplicationResult deduplication) {
        Objects.requireNonNull(deduplication, "deduplication must not be null");
        LookupTargets targets = targets(deduplication);
        if (!crossrefProperties.isEnabled()) {
            // A disabled source performs no port, gate, retry, or HTTP operation.
            return summary(targets, 0, 0, 0, 0, 0, false, false, List.of(), List.of(),
                    targets.candidates().stream().map(target -> result(target,
                            CandidateLookupResult.LookupStatus.SOURCE_DISABLED, List.of(), "CROSSREF_DISABLED"))
                            .toList(), deduplication);
        }

        int attempted = 0;
        int found = 0;
        int notFound = 0;
        int failed = 0;
        int skipped = 0;
        boolean sourceAvailable = true;
        List<CrossrefWorkMetadata> metadata = new ArrayList<>();
        List<CrossrefBibliographicLookupResult> bibliographicResults = new ArrayList<>();
        List<CandidateTarget> ordered = targets.candidates();
        int budget = searchProperties.getMaxCrossrefLookupsPerRequest();
        Map<String, LookupOutcome> cachedOutcomes = new HashMap<>();
        List<CandidateLookupResult> candidateResults = new ArrayList<>();

        for (CandidateTarget target : ordered) {
            if (!target.eligible()) {
                candidateResults.add(result(target, CandidateLookupResult.LookupStatus.NOT_ELIGIBLE,
                        List.of(), "BIBLIOGRAPHIC_TITLE_NOT_ELIGIBLE"));
                continue;
            }
            if (!sourceAvailable) {
                candidateResults.add(result(target, CandidateLookupResult.LookupStatus.SOURCE_UNAVAILABLE,
                        List.of(), "CROSSREF_SOURCE_UNAVAILABLE"));
                continue;
            }
            LookupOutcome cached = cachedOutcomes.get(target.lookupKey());
            if (cached != null) {
                candidateResults.add(result(target, cached.found()
                        ? CandidateLookupResult.LookupStatus.FOUND : CandidateLookupResult.LookupStatus.NOT_FOUND,
                        cached.metadata(), cached.found() ? "CROSSREF_FOUND_REUSED_QUERY" : "CROSSREF_NOT_FOUND_REUSED_QUERY"));
                continue;
            }
            if (attempted >= budget) {
                skipped++;
                candidateResults.add(result(target, CandidateLookupResult.LookupStatus.SKIPPED_BY_LIMIT,
                        List.of(), "CROSSREF_LOOKUP_BUDGET_EXHAUSTED"));
                continue;
            }
            attempted++;
            try {
                LookupOutcome outcome = execute(target);
                cachedOutcomes.put(target.lookupKey(), outcome);
                if (outcome.found()) {
                    found++;
                    metadata.addAll(outcome.metadata());
                } else {
                    notFound++;
                }
                if (outcome.bibliographicResult() != null) {
                    bibliographicResults.add(outcome.bibliographicResult());
                }
                candidateResults.add(result(target, outcome.found()
                        ? CandidateLookupResult.LookupStatus.FOUND : CandidateLookupResult.LookupStatus.NOT_FOUND,
                        outcome.metadata(), outcome.found() ? "CROSSREF_FOUND" : "CROSSREF_NOT_FOUND"));
            } catch (CrossrefApiException exception) {
                if (isConfigurationFailure(exception)) throw exception;
                failed++;
                if (isSourceUnavailable(exception)) {
                    sourceAvailable = false;
                    candidateResults.add(result(target, CandidateLookupResult.LookupStatus.SOURCE_UNAVAILABLE,
                            List.of(), "CROSSREF_SOURCE_UNAVAILABLE"));
                } else {
                    candidateResults.add(result(target, CandidateLookupResult.LookupStatus.FAILED,
                            List.of(), "CROSSREF_LOOKUP_FAILED"));
                }
            }
        }
        return summary(targets, attempted, found, notFound, failed, skipped, true, sourceAvailable,
                metadata, bibliographicResults, candidateResults, deduplication);
    }

    private LookupTargets targets(CandidateDeduplicationResult deduplication) {
        List<CandidateTarget> candidates = new ArrayList<>();
        LinkedHashSet<String> dois = new LinkedHashSet<>();
        LinkedHashSet<String> bibliographicQueries = new LinkedHashSet<>();
        for (NormalizedCandidate normalizedCandidate : deduplication.uniqueCandidates()) {
            CandidatePaper candidate = normalizedCandidate.originalCandidate();
            String doi = normalizedCandidate.normalizedDoi();
            if (doi != null) {
                dois.add(doi);
                candidates.add(CandidateTarget.doi(normalizedCandidate, doi));
                continue;
            }
            CrossrefTitleQueryGuard.Decision title = titleQueryGuard.assess(candidate.title());
            if (!title.allowed()) {
                candidates.add(CandidateTarget.notEligible(normalizedCandidate));
                continue;
            }
            CrossrefBibliographicQuery query = new CrossrefBibliographicQuery(
                    title.normalizedTitle(), firstAuthor(candidate), candidate.publicationYear(), candidate.sourceName());
            bibliographicQueries.add(query.deduplicationKey());
            candidates.add(CandidateTarget.query(normalizedCandidate, query));
        }
        return new LookupTargets(dois.size(), bibliographicQueries.size(), List.copyOf(candidates));
    }

    private String firstAuthor(CandidatePaper candidate) {
        return candidate.authors().stream()
                .map(CandidatePaper.Author::displayName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    private LookupOutcome execute(CandidateTarget target) {
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
            List<CrossrefBibliographicLookupResult> bibliographicResults,
            List<CandidateLookupResult> candidateResults,
            CandidateDeduplicationResult deduplication
    ) {
        return new CrossrefLookupSummary(targets.doiEligibleCount(), targets.titleEligibleCount(), attempted, found,
                notFound, failed, skipped, enabled, available, metadata, bibliographicResults, candidateResults,
                deduplication);
    }

    private static CandidateDeduplicationService defaultDeduplicationService() {
        return defaultDeduplicationService(new DoiNormalizer());
    }

    private static CandidateDeduplicationService defaultDeduplicationService(DoiNormalizer doiNormalizer) {
        return new CandidateDeduplicationService(new CandidateNormalizationService(
                doiNormalizer,
                new OpenAlexIdNormalizer(),
                new TitleNormalizer(),
                new AuthorNormalizer(),
                new VenueNormalizer()
        ));
    }

    private CandidateLookupResult result(
            CandidateTarget target, CandidateLookupResult.LookupStatus status,
            List<CrossrefWorkMetadata> references, String reason
    ) {
        return new CandidateLookupResult(target.candidate(), target.route(), status, references, reason);
    }

    private record LookupTargets(int doiEligibleCount, int titleEligibleCount, List<CandidateTarget> candidates) { }

    private record CandidateTarget(
            NormalizedCandidate candidate,
            CandidateLookupResult.LookupRoute route,
            String doi,
            CrossrefBibliographicQuery query,
            boolean eligible
    ) {
        static CandidateTarget doi(NormalizedCandidate candidate, String doi) {
            return new CandidateTarget(candidate, CandidateLookupResult.LookupRoute.DOI, doi, null, true);
        }
        static CandidateTarget query(NormalizedCandidate candidate, CrossrefBibliographicQuery query) {
            return new CandidateTarget(candidate, CandidateLookupResult.LookupRoute.BIBLIOGRAPHIC, null, query, true);
        }
        static CandidateTarget notEligible(NormalizedCandidate candidate) {
            return new CandidateTarget(candidate, CandidateLookupResult.LookupRoute.NONE, null, null, false);
        }
        String lookupKey() {
            return doi != null ? "doi:" + doi : "bibliographic:" + query.deduplicationKey();
        }
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
