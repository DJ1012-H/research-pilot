package com.dj1012h.researchpilot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.ai.enabled=false")
class ResearchPilotApplicationTests {

    @Test
    void contextLoads() {
    }
}
