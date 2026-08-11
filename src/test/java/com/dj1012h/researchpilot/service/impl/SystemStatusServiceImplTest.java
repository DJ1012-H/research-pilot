package com.dj1012h.researchpilot.service.impl;

import com.dj1012h.researchpilot.dto.response.SystemStatusResponse;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingBatch;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingPort;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingVector;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexPort;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexProbe;
import com.dj1012h.researchpilot.mapper.DatabaseProbeMapper;
import com.dj1012h.researchpilot.service.SystemStatusService;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemStatusServiceImplTest {

    @Mock private ObjectProvider<DatabaseProbeMapper> databaseProbeMapperProvider;
    @Mock private ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    @Mock private ObjectProvider<ChatModel> chatModelProvider;
    @Mock private ObjectProvider<EmbeddingPort> embeddingPortProvider;
    @Mock private ObjectProvider<RagIndexPort> ragIndexPortProvider;
    @Mock private DatabaseProbeMapper databaseProbeMapper;

    @Test
    void shouldReportMySqlUpWhenMyBatisProbeSucceeds() {
        when(databaseProbeMapperProvider.getIfAvailable()).thenReturn(databaseProbeMapper);
        when(databaseProbeMapper.selectOne()).thenReturn(1);
        when(redisTemplateProvider.getIfAvailable()).thenReturn(null);
        when(chatModelProvider.getIfAvailable()).thenReturn(null);
        when(embeddingPortProvider.getIfAvailable()).thenReturn(null);
        when(ragIndexPortProvider.getIfAvailable()).thenReturn(null);

        SystemStatusResponse response = service().check();

        assertThat(response.mysql().status()).isEqualTo("UP");
        assertThat(response.mysql().detail()).isEqualTo("MyBatis SELECT 1 succeeded");
        assertThat(response.ollamaEmbedding().status()).isEqualTo("DISABLED");
        assertThat(response.qdrant().status()).isEqualTo("DISABLED");
    }

    @Test
    void shouldReportMySqlDownWhenMyBatisProbeFails() {
        when(databaseProbeMapperProvider.getIfAvailable()).thenReturn(databaseProbeMapper);
        when(databaseProbeMapper.selectOne()).thenThrow(new IllegalStateException("database unavailable"));
        when(redisTemplateProvider.getIfAvailable()).thenReturn(null);
        when(chatModelProvider.getIfAvailable()).thenReturn(null);
        when(embeddingPortProvider.getIfAvailable()).thenReturn(null);
        when(ragIndexPortProvider.getIfAvailable()).thenReturn(null);

        SystemStatusResponse response = service().check();

        assertThat(response.mysql().status()).isEqualTo("DOWN");
        assertThat(response.mysql().detail()).isEqualTo("IllegalStateException");
        assertThat(response.application()).isEqualTo("UP");
    }

    @Test
    void shouldReportOptionalRagDependenciesSeparatelyFromApplicationLiveness() {
        EmbeddingPort embeddingPort = controlledTexts -> new EmbeddingBatch(
                "test-model",
                List.of(new EmbeddingVector(List.of(0.25, 0.75))),
                2,
                Duration.ofMillis(1));
        RagIndexPort indexPort = mock(RagIndexPort.class);
        when(databaseProbeMapperProvider.getIfAvailable()).thenReturn(null);
        when(redisTemplateProvider.getIfAvailable()).thenReturn(null);
        when(chatModelProvider.getIfAvailable()).thenReturn(null);
        when(embeddingPortProvider.getIfAvailable()).thenReturn(embeddingPort);
        when(ragIndexPortProvider.getIfAvailable()).thenReturn(indexPort);
        when(indexPort.probe()).thenReturn(new RagIndexProbe(false, "QDRANT_HTTP_FAILURE"));

        SystemStatusResponse response = service().check();

        assertThat(response.application()).isEqualTo("UP");
        assertThat(response.ollamaEmbedding().status()).isEqualTo("UP");
        assertThat(response.ollamaEmbedding().detail()).contains("2 dimensions");
        assertThat(response.qdrant().status()).isEqualTo("DOWN");
        assertThat(response.qdrant().detail()).isEqualTo("QDRANT_HTTP_FAILURE");
    }

    private SystemStatusService service() {
        return new SystemStatusServiceImpl(
                databaseProbeMapperProvider,
                redisTemplateProvider,
                chatModelProvider,
                embeddingPortProvider,
                ragIndexPortProvider);
    }
}
