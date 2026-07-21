package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.config.LiteratureSearchProperties;
import com.dj1012h.researchpilot.integration.crossref.CrossrefApiException;
import com.dj1012h.researchpilot.integration.crossref.CrossrefFailureType;
import com.dj1012h.researchpilot.integration.crossref.CrossrefLookupResult;
import com.dj1012h.researchpilot.integration.crossref.CrossrefProperties;
import com.dj1012h.researchpilot.integration.crossref.CrossrefSearchPort;
import com.dj1012h.researchpilot.integration.crossref.CrossrefWorkMetadata;
import com.dj1012h.researchpilot.literature.model.CandidatePaper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Service
public class CrossrefCandidateLookupService {

    private final CrossrefSearchPort crossrefSearchPort;
    private final CrossrefProperties crossrefProperties;
    private final LiteratureSearchProperties searchProperties;

    public CrossrefCandidateLookupService(
            CrossrefSearchPort crossrefSearchPort,
            CrossrefProperties crossrefProperties,
            LiteratureSearchProperties searchProperties
    ) {
        this.crossrefSearchPort = crossrefSearchPort;
        this.crossrefProperties = crossrefProperties;
        this.searchProperties = searchProperties;
    }

    public CrossrefLookupSummary lookup(List<CandidatePaper> candidates) {
        Objects.requireNonNull(candidates, "candidates 不能为空");
        List<String> dois = uniqueEligibleDois(candidates);
        if (!crossrefProperties.isEnabled()) {
            return summary(dois.size(), 0, 0, 0, 0, 0, false, false, List.of());
        }

        int attempted = 0;
        int found = 0;
        int notFound = 0;
        int failed = 0;
        int skipped = 0;
        boolean sourceAvailable = true;
        List<CrossrefWorkMetadata> metadata = new ArrayList<>();
        int budget = searchProperties.getMaxCrossrefLookupsPerRequest();

        for (int index = 0; index < dois.size(); index++) {
            if (attempted >= budget) {
                skipped += dois.size() - index;
                break;
            }
            attempted++;
            try {
                CrossrefLookupResult result = crossrefSearchPort.findByDoi(dois.get(index));
                if (result.status() == CrossrefLookupResult.Status.FOUND) {
                    found++;
                    metadata.add(result.metadata());
                } else {
                    notFound++;
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
        return summary(dois.size(), attempted, found, notFound, failed, skipped, true, sourceAvailable, metadata);
    }

    private List<String> uniqueEligibleDois(List<CandidatePaper> candidates) {
        LinkedHashSet<String> dois = new LinkedHashSet<>();
        for (CandidatePaper candidate : candidates) {
            if (candidate != null && StringUtils.hasText(candidate.doi())) dois.add(candidate.doi().trim());
        }
        return List.copyOf(dois);
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

    private CrossrefLookupSummary summary(int eligible, int attempted, int found, int notFound, int failed,
                                          int skipped, boolean enabled, boolean available,
                                          List<CrossrefWorkMetadata> metadata) {
        return new CrossrefLookupSummary(eligible, attempted, found, notFound, failed, skipped,
                enabled, available, metadata);
    }
}
