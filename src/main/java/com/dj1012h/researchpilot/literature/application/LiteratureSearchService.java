package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchPort;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchResult;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
import com.dj1012h.researchpilot.literature.model.CandidateVerificationOutcome;
import com.dj1012h.researchpilot.literature.model.OpenAlexQuery;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Executes the trusted retrieval, Crossref verification, and formal-output chain. */
@Service
public class LiteratureSearchService {

    private static final Logger log = LoggerFactory.getLogger(LiteratureSearchService.class);

    private final SearchAgent searchAgent;
    private final OpenAlexQueryFactory queryFactory;
    private final OpenAlexSearchPort openAlexSearchPort;
    private final CrossrefCandidateLookupService crossrefCandidateLookupService;
    private final PaperVerificationService paperVerificationService;
    private final EligiblePaperFilter eligiblePaperFilter;
    private final Clock clock;

    public LiteratureSearchService(
            SearchAgent searchAgent,
            OpenAlexQueryFactory queryFactory,
            OpenAlexSearchPort openAlexSearchPort,
            CrossrefCandidateLookupService crossrefCandidateLookupService,
            PaperVerificationService paperVerificationService,
            EligiblePaperFilter eligiblePaperFilter,
            Clock clock
    ) {
        this.searchAgent = Objects.requireNonNull(searchAgent, "searchAgent must not be null");
        this.queryFactory = Objects.requireNonNull(queryFactory, "queryFactory must not be null");
        this.openAlexSearchPort = Objects.requireNonNull(openAlexSearchPort, "openAlexSearchPort must not be null");
        this.crossrefCandidateLookupService = Objects.requireNonNull(
                crossrefCandidateLookupService, "crossrefCandidateLookupService must not be null");
        this.paperVerificationService = Objects.requireNonNull(paperVerificationService,
                "paperVerificationService must not be null");
        this.eligiblePaperFilter = Objects.requireNonNull(eligiblePaperFilter, "eligiblePaperFilter must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public SearchResponse search(SearchRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        UUID taskId = UUID.randomUUID();
        Instant startedAt = Instant.now(clock);

        SearchPlan plan = searchAgent.createPlan(request);
        OpenAlexQuery query = queryFactory.create(plan);
        OpenAlexSearchResult result = openAlexSearchPort.search(query);
        CrossrefLookupSummary crossref = crossrefCandidateLookupService.lookup(result.candidates());
        List<CandidateVerificationOutcome> outcomes = paperVerificationService.verify(crossref);
        List<SearchResponse.PaperResult> papers = eligiblePaperFilter.filter(outcomes, plan.resultLimit());
        SearchResponse.VerificationSummary verificationSummary = verificationSummary(outcomes);

        Instant completedAt = Instant.now(clock);
        long elapsedMs = Math.max(0, completedAt.toEpochMilli() - startedAt.toEpochMilli());
        SearchResponse response = new SearchResponse(
                taskId,
                status(papers, plan.resultLimit()),
                plan,
                result.candidates().size(),
                crossref.candidateDeduplication().uniqueCount(),
                verificationSummary,
                papers,
                message(result.candidates().size(), crossref, verificationSummary, papers.size()),
                elapsedMs,
                completedAt
        );

        log.info(
                "event=literature_search_completed taskId={} candidateCount={} uniqueCandidateCount={} "
                        + "crossrefAttemptedCount={} crossrefFoundCount={} crossrefNotFoundCount={} "
                        + "crossrefFailedCount={} crossrefSourceAvailable={} verifiedCount={} formalResultCount={} elapsedMs={}",
                taskId, result.candidates().size(), crossref.candidateDeduplication().uniqueCount(),
                crossref.attemptedCount(), crossref.foundCount(), crossref.notFoundCount(), crossref.failedCount(),
                crossref.sourceAvailable(), verificationSummary.verifiedCount(), papers.size(), elapsedMs
        );
        return response;
    }

    private SearchResponse.SearchStatus status(List<SearchResponse.PaperResult> papers, int resultLimit) {
        if (papers.isEmpty()) return SearchResponse.SearchStatus.NO_VERIFIED_RESULTS;
        return papers.size() < resultLimit
                ? SearchResponse.SearchStatus.PARTIAL_SUCCESS
                : SearchResponse.SearchStatus.COMPLETED;
    }

    private SearchResponse.VerificationSummary verificationSummary(List<CandidateVerificationOutcome> outcomes) {
        int verified = 0;
        int partial = 0;
        int unverified = 0;
        int rejected = 0;
        for (CandidateVerificationOutcome outcome : outcomes) {
            switch (outcome.verification().status()) {
                case VERIFIED -> verified++;
                case PARTIALLY_VERIFIED -> partial++;
                case NOT_CHECKED, NOT_FOUND, SOURCE_UNAVAILABLE -> unverified++;
                case CONFLICTED, REJECTED -> rejected++;
            }
        }
        return new SearchResponse.VerificationSummary(verified, partial, unverified, rejected);
    }

    private String message(
            int candidateCount,
            CrossrefLookupSummary crossref,
            SearchResponse.VerificationSummary verificationSummary,
            int formalResultCount
    ) {
        if (candidateCount == 0) {
            return "未检索到候选论文；Crossref 未尝试查询，且未返回正式论文。";
        }
        return "OpenAlex candidates=" + candidateCount
                + "; deduplicated=" + crossref.candidateDeduplication().uniqueCount()
                + "; Crossref attempted=" + crossref.attemptedCount()
                + ", found=" + crossref.foundCount()
                + ", notFound=" + crossref.notFoundCount()
                + ", failed=" + crossref.failedCount()
                + ", skipped=" + crossref.skippedByLimitCount()
                + "; verified=" + verificationSummary.verifiedCount()
                + ", partiallyVerified=" + verificationSummary.partiallyVerifiedCount()
                + ", unverified=" + verificationSummary.unverifiedCount()
                + ", rejected=" + verificationSummary.rejectedCount()
                + "; formalPapers=" + formalResultCount
                + (crossref.crossrefEnabled() ? "" : "；尚未执行字段级核验")
                + (crossref.sourceAvailable() ? "" : "; Crossref source unavailable.");
    }
}
