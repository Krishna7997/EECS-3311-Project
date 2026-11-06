package com.maplecfo.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Local settings, read from environment variables first and then from a
 * {@code .env} file (the project folder, then ~/.maplecfo/.env). The real
 * API key lives only in the git-ignored .env file, never in the source code.
 *
 * <pre>
 * LLM_API_KEY=...            # Anthropic key; leave empty to run with the mock model
 * LLM_MODEL=claude-haiku-5-5 # optional
 * LLM_WORKSPACE_ID=...       # optional, only for keys that are not tied to one workspace
 * </pre>
 */
public final class AppConfig {

    public static final String DEFAULT_MODEL = "claude-haiku-5-5";

    private final Map<String, String> values;

    private AppConfig(Map<String, String> values) {
        this.values = values;
    }

    /** Settings from the real environment and the usual .env locations. */
    public static AppConfig load() {
        Path home = Path.of(System.getProperty("user.home"), ".maplecfo", ".env");
        return load(System.getenv(), List.of(Path.of(".env"), home));
    }

    static AppConfig load(Map<String, String> env, List<Path> envFiles) {
        Map<String, String> v = new HashMap<>();
        // later files must not override earlier ones, and the environment wins over files
        for (int i = envFiles.size() - 1; i >= 0; i--) {
            v.putAll(readEnvFile(envFiles.get(i)));
        }
        env.forEach((k, val) -> {
            if (k.startsWith("LLM_") || k.startsWith("MAPLECFO_")) {
                v.put(k, val);
            }
        });
        return new AppConfig(v);
    }

    /** Any MAPLECFO_* or LLM_* value, from the environment or .env; empty when not set. */
    public java.util.Optional<String> get(String key) {
        String v = values.get(key);
        return v == null || v.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(v);
    }

    /** Values for tests or tools that set the configuration directly. */
    public static AppConfig of(Map<String, String> values) {
        return new AppConfig(new HashMap<>(values));
    }

    static Map<String, String> readEnvFile(Path file) {
        Map<String, String> v = new HashMap<>();
        if (!Files.isRegularFile(file)) {
            return v;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String l = line.trim();
                if (l.isEmpty() || l.startsWith("#") || !l.contains("=")) continue;
                String key = l.substring(0, l.indexOf('=')).trim();
                String val = l.substring(l.indexOf('=') + 1).trim();
                if (val.length() >= 2 && (val.startsWith("\"") && val.endsWith("\"") || val.startsWith("'") && val.endsWith("'"))) {
                    val = val.substring(1, val.length() - 1);
                }
                v.put(key, val);
            }
        } catch (IOException e) {
            // an unreadable .env is treated like a missing one: the app runs with the mock model
        }
        return v;
    }

    /** The API key, or empty when none is set or it is still the placeholder. */
    public Optional<String> apiKey() {
        String k = values.get("LLM_API_KEY");
        if (k == null || k.isBlank() || k.startsWith("YOUR_")) {
            return Optional.empty();
        }
        return Optional.of(k.trim());
    }

    public String model() {
        String m = values.get("LLM_MODEL");
        return m == null || m.isBlank() ? DEFAULT_MODEL : m.trim();
    }

    public Optional<String> workspaceId() {
        String w = values.get("LLM_WORKSPACE_ID");
        return w == null || w.isBlank() ? Optional.empty() : Optional.of(w.trim());
    }
}
