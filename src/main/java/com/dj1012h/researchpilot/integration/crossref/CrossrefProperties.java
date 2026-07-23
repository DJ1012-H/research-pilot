package com.dj1012h.researchpilot.integration.crossref;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.crossref")
public class CrossrefProperties {

    private boolean enabled;
    private String baseUrl = "https://api.crossref.org";
    private String mailto;
    private String userAgent = "ResearchPilot/0.1";
    private String plusToken;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(10);
    private int maxConcurrency = 2;
    private int requestsPerSecond = 5;
    private int maxRetries = 2;
    private Duration initialBackoff = Duration.ofMillis(250);
    private Duration maxBackoff = Duration.ofSeconds(2);
    private int bibliographicRows = 5;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getMailto() { return mailto; }
    public void setMailto(String mailto) { this.mailto = mailto; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getPlusToken() { return plusToken; }
    public void setPlusToken(String plusToken) { this.plusToken = plusToken; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }
    public int getRequestsPerSecond() { return requestsPerSecond; }
    public void setRequestsPerSecond(int requestsPerSecond) { this.requestsPerSecond = requestsPerSecond; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
    public Duration getInitialBackoff() { return initialBackoff; }
    public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff; }
    public Duration getMaxBackoff() { return maxBackoff; }
    public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff; }
    public int getBibliographicRows() { return bibliographicRows; }
    public void setBibliographicRows(int bibliographicRows) { this.bibliographicRows = bibliographicRows; }
}
