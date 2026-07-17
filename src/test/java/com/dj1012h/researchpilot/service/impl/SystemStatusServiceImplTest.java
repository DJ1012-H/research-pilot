package com.dj1012h.researchpilot.service.impl;

import com.dj1012h.researchpilot.dto.response.SystemStatusResponse;
import com.dj1012h.researchpilot.mapper.DatabaseProbeMapper;
import com.dj1012h.researchpilot.service.SystemStatusService;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemStatusServiceImplTest {

    @Mock
    private ObjectProvider<DatabaseProbeMapper> databaseProbeMapperProvider;

    @Mock
    private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    @Mock
    private ObjectProvider<ChatModel> chatModelProvider;

    @Mock
    private DatabaseProbeMapper databaseProbeMapper;

    @Test
    void shouldReportMySqlUpWhenMyBatisProbeSucceeds() {
        when(databaseProbeMapperProvider.getIfAvailable()).thenReturn(databaseProbeMapper);
        when(databaseProbeMapper.selectOne()).thenReturn(1);
        when(redisTemplateProvider.getIfAvailable()).thenReturn(null);
        when(chatModelProvider.getIfAvailable()).thenReturn(null);

        SystemStatusService service = new SystemStatusServiceImpl(
                databaseProbeMapperProvider,
                redisTemplateProvider,
                chatModelProvider
        );

        SystemStatusResponse response = service.check();

        assertThat(response.mysql().status()).isEqualTo("UP");
        assertThat(response.mysql().detail()).isEqualTo("MyBatis SELECT 1 succeeded");
    }

    @Test
    void shouldReportMySqlDownWhenMyBatisProbeFails() {
        when(databaseProbeMapperProvider.getIfAvailable()).thenReturn(databaseProbeMapper);
        when(databaseProbeMapper.selectOne()).thenThrow(new IllegalStateException("database unavailable"));
        when(redisTemplateProvider.getIfAvailable()).thenReturn(null);
        when(chatModelProvider.getIfAvailable()).thenReturn(null);

        SystemStatusService service = new SystemStatusServiceImpl(
                databaseProbeMapperProvider,
                redisTemplateProvider,
                chatModelProvider
        );

        SystemStatusResponse response = service.check();

        assertThat(response.mysql().status()).isEqualTo("DOWN");
        assertThat(response.mysql().detail()).isEqualTo("IllegalStateException");
    }
}
