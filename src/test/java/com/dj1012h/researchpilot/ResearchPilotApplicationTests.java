package com.dj1012h.researchpilot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.ai.enabled=false")
class ResearchPilotApplicationTests {

    @Autowired
    private Environment environment;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldPersistLogsWithoutExposingTheLogfileActuatorEndpoint() {
        assertThat(environment.getProperty("logging.file.name")).isNotBlank();
        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health,info");
        assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("false");
    }
}
