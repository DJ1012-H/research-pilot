package com.dj1012h.researchpilot.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Isolated JSON mapper for untrusted model structured output.
 *
 * <p>The strict {@link ObjectMapper} is created by a regular factory method,
 * not registered as an {@link ObjectMapper} bean. This keeps Spring Boot's
 * auto-configured MVC mapper, including its Java time modules, intact.</p>
 */
@Configuration(proxyBeanMethods = false)
public class StructuredOutputConfiguration {

    public static final String OBJECT_MAPPER_BEAN = "structuredOutputObjectMapper";

    @Bean(OBJECT_MAPPER_BEAN)
    public StructuredOutputMapper structuredOutputMapper() {
        return new StructuredOutputMapper(structuredOutputObjectMapper());
    }

    public ObjectMapper structuredOutputObjectMapper() {
        return JsonMapper.builder()
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .disable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                .disable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
                .disable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .build();
    }
}
