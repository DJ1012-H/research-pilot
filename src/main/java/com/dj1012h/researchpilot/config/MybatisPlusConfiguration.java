package com.dj1012h.researchpilot.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan({
        "com.dj1012h.researchpilot.mapper",
        "com.dj1012h.researchpilot.literature.persistence.mapper"
})
public class MybatisPlusConfiguration {
}
