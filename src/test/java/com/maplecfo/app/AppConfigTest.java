package com.maplecfo.app;

import com.maplecfo.agent.llm.ClaudeClientAdapter;
import com.maplecfo.agent.llm.LlmClientFactory;
import com.maplecfo.agent.llm.MockLlmClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @TempDir
    Path dir;

    @Test
    void readsDotEnvAndIgnoresThePlaceholder() throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, "# comment\nLLM_API_KEY=\"sk-ant-api03-test\"\nLLM_MODEL=claude-haiku-5-5\n");
        AppConfig c = AppConfig.load(Map.of(), List.of(env));
        assertEquals("sk-ant-api03-test", c.apiKey().orElseThrow());
        assertEquals("claude-haiku-5-5", c.model());

        Files.writeString(env, "LLM_API_KEY=YOUR_LOCAL_API_KEY_HERE\n");
        assertTrue(AppConfig.load(Map.of(), List.of(env)).apiKey().isEmpty());
    }

    @Test
    void environmentWinsOverFileAndMissingFileIsFine() throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, "LLM_API_KEY=from-file\n");
        assertEquals("from-env", AppConfig.load(Map.of("LLM_API_KEY", "from-env"), List.of(env)).apiKey().orElseThrow());
        assertTrue(AppConfig.load(Map.of(), List.of(dir.resolve("missing.env"))).apiKey().isEmpty());
    }

    @Test
    void factoryPicksClaudeOnlyWithAKey() {
        assertInstanceOf(MockLlmClient.class, LlmClientFactory.create(AppConfig.of(Map.of()), List.of()));
        assertInstanceOf(ClaudeClientAdapter.class,
                LlmClientFactory.create(AppConfig.of(Map.of("LLM_API_KEY", "sk-test")), List.of()));
    }
}
