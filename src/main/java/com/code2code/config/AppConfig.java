package com.code2code.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration class for the .NET to Java converter.
 * Loads properties from application.properties and provides typed access.
 */
@Component
public class AppConfig {

    // LLM Configuration
    @Value("${google.api.key:}")
    private String googleApiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${llm.routing.tier:balanced}")
    private String llmRoutingTier;

    // RAG Configuration
    @Value("${chroma.host:localhost}")
    private String chromaHost;

    @Value("${chroma.port:8000}")
    private int chromaPort;

    @Value("${chroma.collection:dotnet_java_patterns}")
    private String chromaCollection;

    // Conversion Settings
    @Value("${max.retries:3}")
    private int maxRetries;

    @Value("${context.budget:128000}")
    private int contextBudget;

    @Value("${enable.observability:true}")
    private boolean enableObservability;

    // Getters
    public String getGoogleApiKey() { return googleApiKey; }
    public String getGeminiModel() { return geminiModel; }
    public String getLlmRoutingTier() { return llmRoutingTier; }
    public String getChromaHost() { return chromaHost; }
    public int getChromaPort() { return chromaPort; }
    public String getChromaCollection() { return chromaCollection; }
    public int getMaxRetries() { return maxRetries; }
    public int getContextBudget() { return contextBudget; }
    public boolean isObservabilityEnabled() { return enableObservability; }
}
