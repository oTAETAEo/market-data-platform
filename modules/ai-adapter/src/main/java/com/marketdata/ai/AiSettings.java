package com.marketdata.ai;

import java.util.Locale;

public final class AiSettings {
    private final String provider;
    private final String quickModel;
    private final String deepModel;
    private final String apiKey;

    public AiSettings(String provider, String quickModel, String deepModel, String apiKey) {
        this.provider = required(provider, "provider").toLowerCase(Locale.ROOT);
        this.quickModel = required(quickModel, "quickModel");
        this.deepModel = required(deepModel, "deepModel");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public String provider() { return provider; }
    public String quickModel() { return quickModel; }
    public String deepModel() { return deepModel; }
    public String apiKey() { return apiKey; }

    public boolean configured() {
        return switch (provider) {
            case "ollama", "openai_compatible", "bedrock" -> true;
            default -> !apiKey.isBlank();
        };
    }

    @Override
    public String toString() {
        return "AiSettings[provider=" + provider + ", quickModel=" + quickModel
                + ", deepModel=" + deepModel + ", apiKey=<redacted>]";
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
