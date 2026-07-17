package com.dj1012h.researchpilot.service.impl;

import com.dj1012h.researchpilot.dto.response.DependencyStatusResponse;
import com.dj1012h.researchpilot.dto.response.SystemStatusResponse;
import com.dj1012h.researchpilot.mapper.DatabaseProbeMapper;
import com.dj1012h.researchpilot.service.SystemStatusService;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusServiceImpl implements SystemStatusService {
    //Mysql探测
    private final ObjectProvider<DatabaseProbeMapper> databaseProbeMapperProvider;
    //redis探测
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    //LLM可用性分析
    private final ObjectProvider<ChatModel> chatModelProvider;

    public SystemStatusServiceImpl(ObjectProvider<DatabaseProbeMapper> databaseProbeMapperProvider,
                                   ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                   ObjectProvider<ChatModel> chatModelProvider) {
        this.databaseProbeMapperProvider = databaseProbeMapperProvider;
        this.redisTemplateProvider = redisTemplateProvider;
        this.chatModelProvider = chatModelProvider;
    }

    @Override
    public SystemStatusResponse check() {
        return new SystemStatusResponse(
                "UP",
                checkMySql(),
                checkRedis(),
                chatModelProvider.getIfAvailable() != null
        );
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

    private DependencyStatusResponse failed(RuntimeException exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return new DependencyStatusResponse("DOWN", rootCause.getClass().getSimpleName());
    }
}
