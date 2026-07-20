package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchPort;
import com.dj1012h.researchpilot.integration.openalex.OpenAlexSearchResult;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.api.dto.SearchResponse;
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

/**
 * Executes the currently available single-provider literature search chain.
 *
 * <p>Candidate verification is deliberately outside this implementation
 * stage. Consequently, OpenAlex candidates are counted but never exposed as
 * formally verified papers.</p>
 */
@Service
public class LiteratureSearchService {

    private static final Logger log = LoggerFactory.getLogger(LiteratureSearchService.class);

    private final SearchAgent searchAgent;
    private final OpenAlexQueryFactory queryFactory;
    private final OpenAlexSearchPort openAlexSearchPort;
    private final Clock clock;

    public LiteratureSearchService(
            SearchAgent searchAgent,
            OpenAlexQueryFactory queryFactory,
            OpenAlexSearchPort openAlexSearchPort,
            Clock clock
    ) {
        this.searchAgent = searchAgent;
        this.queryFactory = queryFactory;
        this.openAlexSearchPort = openAlexSearchPort;
        this.clock = clock;
    }

    public SearchResponse search(SearchRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        UUID taskId = UUID.randomUUID();
        Instant startedAt = Instant.now(clock);

        SearchPlan plan = searchAgent.createPlan(request);
        OpenAlexQuery query = queryFactory.create(plan);
        OpenAlexSearchResult result = openAlexSearchPort.search(query);

        int candidateCount = result.candidates().size();
        Instant completedAt = Instant.now(clock);
        long elapsedMs = Math.max(0, completedAt.toEpochMilli() - startedAt.toEpochMilli());
        SearchResponse response = new SearchResponse(
                taskId,
                SearchResponse.SearchStatus.NO_VERIFIED_RESULTS,
                plan,
                candidateCount,
                0,
                new SearchResponse.VerificationSummary(0, 0, 0, 0),
                List.of(),
                candidateCount == 0
                        ? "未检索到候选论文"
                        : "已检索到候选论文，但尚未经过外部核验，当前不返回正式论文",
                elapsedMs,
                completedAt
        );

        log.info(
                "event=literature_search_completed taskId={} candidateCount={} formalResultCount=0 elapsedMs={}",
                taskId,
                candidateCount,
                elapsedMs
        );
        return response;
    }
}
