package com.dj1012h.researchpilot.literature.rag.index;

import com.dj1012h.researchpilot.literature.rag.VerifiedPaperProjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class RagIndexingConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagIndexingConfig.class);

    @Bean
    @ConditionalOnProperty(name = {
            "app.rag.indexing.enabled",
            "app.rag.embedding.ollama.enabled",
            "app.rag.qdrant.enabled",
            "app.literature.persistence.enabled"
    }, havingValue = "true")
    RagIndexRebuildService ragIndexRebuildService(
            VerifiedPaperSourceRepository sourceRepository,
            VerifiedPaperProjector projector,
            RagIndexPort indexPort,
            RagIndexStateStore stateStore,
            RagIndexDefinition definition,
            @Qualifier("systemClock") Clock clock
    ) {
        return new RagIndexRebuildService(
                sourceRepository,
                projector,
                indexPort,
                stateStore,
                definition,
                clock);
    }

    @Bean
    @ConditionalOnProperty(name = {
            "app.rag.indexing.enabled",
            "app.rag.indexing.rebuild-on-startup",
            "app.rag.embedding.ollama.enabled",
            "app.rag.qdrant.enabled",
            "app.literature.persistence.enabled"
    }, havingValue = "true")
    ApplicationRunner ragIndexRebuildRunner(RagIndexRebuildService rebuildService) {
        return arguments -> {
            RagIndexRebuildResult result = rebuildService.rebuild();
            LOGGER.info(
                    "Trusted RAG rebuild completed: sourcePapers={}, points={}, embeddedPapers={}, skippedEmbeddingPapers={}, payloadOnlyUpdates={}, deletedPoints={}",
                    result.sourcePaperCount(),
                    result.actualPointCount(),
                    result.embeddedPaperCount(),
                    result.skippedEmbeddingPaperCount(),
                    result.payloadOnlyUpdateCount(),
                    result.deletedPointCount());
        };
    }
}
