package com.maplecfo.agent.llm;

import com.maplecfo.app.AppConfig;

import java.util.List;

/**
 * Factory Method pattern: decides which {@link LlmClient} to create from the
 * local configuration. With an API key it returns the Claude adapter; without
 * one the app still works in demo mode with the mock.
 */
public final class LlmClientFactory {

    private LlmClientFactory() {
    }

    public static LlmClient create(AppConfig config, List<MockLlmClient.Rule> demoRules) {
        if (hasApiKey(config)) {
            return new ClaudeClientAdapter(config.apiKey().orElseThrow(), config.model(), config.workspaceId());
        }
        return new MockLlmClient(demoRules);
    }

    public static boolean hasApiKey(AppConfig config) {
        return config.apiKey().isPresent();
    }
}
