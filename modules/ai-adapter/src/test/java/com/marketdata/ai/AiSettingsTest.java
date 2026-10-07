package com.marketdata.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiSettingsTest {
    @Test
    void redactsKeysAndRequiresHostedProviderCredential() {
        AiSettings hosted = new AiSettings("OpenAI", "quick", "deep", "secret-value");
        assertThat(hosted.configured()).isTrue();
        assertThat(hosted.toString()).doesNotContain("secret-value").contains("<redacted>");
        assertThat(new AiSettings("openai", "quick", "deep", "").configured()).isFalse();
        assertThat(new AiSettings("ollama", "quick", "deep", "").configured()).isTrue();
    }
}
