package com.dj1012h.researchpilot.integration.ollama;

import com.dj1012h.researchpilot.literature.rag.embedding.RagEmbeddingProfile;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.rag.embedding.ollama")
public class OllamaEmbeddingProperties {

    private boolean enabled;
    private String baseUrl = "http://127.0.0.1:11434";
    private String model = RagEmbeddingProfile.INITIAL_MODEL;
    private String embeddingVersion = RagEmbeddingProfile.INITIAL_VERSION;
    private int expectedDimensions = RagEmbeddingProfile.INITIAL_DIMENSIONS;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(60);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getEmbeddingVersion() { return embeddingVersion; }
    public void setEmbeddingVersion(String embeddingVersion) { this.embeddingVersion = embeddingVersion; }
    public int getExpectedDimensions() { return expectedDimensions; }
    public void setExpectedDimensions(int expectedDimensions) { this.expectedDimensions = expectedDimensions; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }

    public RagEmbeddingProfile profile() {
        return new RagEmbeddingProfile(model, embeddingVersion, expectedDimensions);
    }
}
