package com.dj1012h.researchpilot.service.impl;

import com.dj1012h.researchpilot.dto.response.DependencyStatusResponse;
import com.dj1012h.researchpilot.dto.response.SystemStatusResponse;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingBatch;
import com.dj1012h.researchpilot.literature.rag.embedding.EmbeddingPort;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexPort;
import com.dj1012h.researchpilot.literature.rag.index.RagIndexProbe;
import com.dj1012h.researchpilot.mapper.DatabaseProbeMapper;
import com.dj1012h.researchpilot.service.SystemStatusService;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SystemStatusServiceImpl implements SystemStatusService {

    private final ObjectProvider<DatabaseProbeMapper> databaseProbeMapperProvider;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectProvider<EmbeddingPort> embeddingPortProvider;
    private final ObjectProvider<RagIndexPort> ragIndexPortProvider;

    public SystemStatusServiceImpl(
            ObjectProvider<DatabaseProbeMapper> databaseProbeMapperProvider,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<EmbeddingPort> embeddingPortProvider,
            ObjectProvider<RagIndexPort> ragIndexPortProvider
    ) {
        this.databaseProbeMapperProvider = databaseProbeMapperProvider;
        this.redisTemplateProvider = redisTemplateProvider;
        this.chatModelProvider = chatModelProvider;
        this.embeddingPortProvider = embeddingPortProvider;
        this.ragIndexPortProvider = ragIndexPortProvider;
    }

    @Override
    public SystemStatusResponse check() {
        return new SystemStatusResponse(
                "UP",
                checkMySql(),
                checkRedis(),
                checkOllamaEmbedding(),
                checkQdrant(),
                chatModelProvider.getIfAvailable() != null);
    }

    private DependencyStatusResponse checkMySql() {
        DatabaseProbeMapper databaseProbeMapper = databaseProbeMapperProvider.getIfAvailable();
        if (databaseProbeMapper == null) {
            return new DependencyStatusResponse("DISABLED", "DatabaseProbeMapper bean is unavailable");
        }
        try {
            Integer result = databaseProbeMapper.selectOne();
            return Integer.valueOf(1).equals(result)
                    ? new DependencyStatusResponse("UP", "MyBatis SELECT 1 succeeded")
                    : new DependencyStatusResponse("DOWN", "Unexpected SELECT 1 result");
        } catch (RuntimeException exception) {
            return failed(exception);
        }
    }

    private DependencyStatusResponse checkRedis() {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return new DependencyStatusResponse("DISABLED", "StringRedisTemplate bean is unavailable");
        }
        try {
            RedisCallback<String> ping = connection -> connection.ping();
            String result = redisTemplate.execute(ping);
            return "PONG".equalsIgnoreCase(result)
                    ? new DependencyStatusResponse("UP", "PING returned PONG")
                    : new DependencyStatusResponse("DOWN", "Unexpected PING result");
        } catch (RuntimeException exception) {
            return failed(exception);
        }
    }

    private DependencyStatusResponse checkOllamaEmbedding() {
        EmbeddingPort embeddingPort = embeddingPortProvider.getIfAvailable();
        if (embeddingPort == null) {
            return new DependencyStatusResponse("DISABLED", "Ollama embedding bean is unavailable");
        }
        try {
            EmbeddingBatch batch = embeddingPort.embed(List.of("ResearchPilot dependency readiness probe"));
            return new DependencyStatusResponse(
                    "UP",
                    "Embedding probe succeeded with " + batch.dimensions() + " dimensions");
        } catch (RuntimeException exception) {
            return failed(exception);
        }
    }

    private DependencyStatusResponse checkQdrant() {
        RagIndexPort indexPort = ragIndexPortProvider.getIfAvailable();
        if (indexPort == null) {
            return new DependencyStatusResponse("DISABLED", "Qdrant index bean is unavailable");
        }
        try {
            RagIndexProbe probe = indexPort.probe();
            return new DependencyStatusResponse(probe.available() ? "UP" : "DOWN", probe.detail());
        } catch (RuntimeException exception) {
            return failed(exception);
        }
    }

    private DependencyStatusResponse failed(RuntimeException exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return new DependencyStatusResponse("DOWN", rootCause.getClass().getSimpleName());
    }
}
