package com.maplecfo.automation.limits;

import com.maplecfo.planning.ContributionLimits;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * F15 on-disk copy of the last downloaded limits (~/.maplecfo/limits_cache.json). Also a
 * {@link LimitsProvider}, so the app keeps the newer limits after a restart, even offline.
 */
public class LimitsCache implements LimitsProvider {

    private final Path file;

    public LimitsCache(Path file) {
        this.file = file;
    }

    public void save(String json) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** Marks the cache as checked just now without changing it. */
    public void touch() throws IOException {
        if (Files.exists(file)) {
            Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.from(Instant.now()));
        }
    }

    public Optional<Instant> lastChecked() {
        try {
            return Files.exists(file) ? Optional.of(Files.getLastModifiedTime(file).toInstant()) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ContributionLimits> load() {
        try {
            if (!Files.exists(file)) return Optional.empty();
            byte[] bytes = Files.readAllBytes(file);
            return Optional.of(ContributionLimits.loadFrom(new ByteArrayInputStream(bytes)));
        } catch (IOException e) {
            return Optional.empty(); // a broken cache is ignored; the bundled file is used instead
        }
    }

    @Override
    public String name() {
        return "cache";
    }
}
