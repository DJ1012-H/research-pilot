package com.dj1012h.researchpilot.literature.application;

import com.dj1012h.researchpilot.config.AiProperties;
import com.dj1012h.researchpilot.literature.api.dto.SearchRequest;
import com.dj1012h.researchpilot.literature.model.SearchPlan;
import com.dj1012h.researchpilot.literature.validation.SearchPlanValidationException;
import com.dj1012h.researchpilot.literature.validation.SearchPlanValidationPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates model generation and validation of one executable search plan.
 *
 * <p>This component does not call a literature provider. Model failures are
 * deliberately propagated without structured-output retries.</p>
 */
@Component
public class SearchAgent {

    private static final Logger log = LoggerFactory.getLogger(SearchAgent.class);

    private final LlmQueryPlanner queryPlanner;
    private final SearchPlanValidationPipeline validationPipeline;
    private final Clock clock;
    private final int maxValidationRetries;

    public SearchAgent(
            LlmQueryPlanner queryPlanner,
            SearchPlanValidationPipeline validationPipeline,
            AiProperties aiProperties,
            Clock clock
    ) {
        this.queryPlanner = queryPlanner;
        this.validationPipeline = validationPipeline;
        this.clock = clock;
        this.maxValidationRetries = aiProperties.getStructuredOutput().getMaxValidationRetries();
        if (maxValidationRetries < 0 || maxValidationRetries > 1) {
            throw new IllegalStateException("structured output validation retries 必须为 0 或 1");
        }
    }

    public SearchPlan createPlan(SearchRequest request) {
        Objects.requireNonNull(request, "request 不能为空");
        SearchPlanGenerationContext context = SearchPlanGenerationContext.create(request, clock);
        long startNanos = System.nanoTime();

        String rawOutput = queryPlanner.generate(context);
        try {
            SearchPlan plan = validationPipeline.validate(context, rawOutput);
            logSuccess(context, 1, startNanos);
            return plan;
        } catch (SearchPlanValidationException firstFailure) {
            logValidationFailure(context, 1, firstFailure);
            if (maxValidationRetries == 0 || !firstFailure.isRetryable()) {
                throw new SearchPlanGenerationException(firstFailure);
            }

            String correctedOutput = queryPlanner.regenerate(context, firstFailure.getIssues());
            try {
                SearchPlan plan = validationPipeline.validate(context, correctedOutput);
                logSuccess(context, 2, startNanos);
                return plan;
            } catch (SearchPlanValidationException finalFailure) {
                logValidationFailure(context, 2, finalFailure);
                throw new SearchPlanGenerationException(finalFailure);
            }
        }
    }

    private void logSuccess(
            SearchPlanGenerationContext context,
            int attempt,
            long startNanos
    ) {
        log.info(
                "event=search_plan_generated requestId={} attempt={} durationMs={}",
                context.requestId(),
                attempt,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        );
    }

    private void logValidationFailure(
            SearchPlanGenerationContext context,
            int attempt,
            SearchPlanValidationException exception
    ) {
        log.info(
                "event=search_plan_validation_failed requestId={} attempt={} stage={} codes={} retryable={}",
                context.requestId(),
                attempt,
                exception.getStage(),
                exception.getIssues().stream().map(issue -> issue.code()).toList(),
                exception.isRetryable()
        );
    }
}
