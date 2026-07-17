package com.dj1012h.researchpilot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI researchPilotOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ResearchPilot API")
                .version("v1")
                .description("基于 LangChain4j 与 RAG 的学术文献检索 Agent API"));
    }
}
