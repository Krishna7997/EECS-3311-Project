package com.maplecfo.automation;

import com.maplecfo.importer.ColumnMapping;
import com.maplecfo.importer.ImportResult;
import com.maplecfo.services.TransactionService;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * F13 automatic import: watches an "inbox" folder for bank CSV exports. When a new file appears and
 * has stopped growing, the matching column profile is picked automatically and the file is imported.
 * Duplicate rows are skipped by the importer, so dropping the same export twice is harmless.
 */
public class ImportFolderWatcher implements AutoCloseable {

    /** Result of one file, shown to the user as a message. */
    public record Outcome(Path file, boolean imported, String message) {
    }

    static final long STABLE_WAIT_MS = 400;

    private final Path folder;
    private final TransactionService txService;
    private final Supplier<List<String>> accountIds;
    private final String defaultAccountId;
    private final Consumer<Outcome> listener;
    private final Set<String> processed = new HashSet<>();
    private WatchService watchService;
    private Thread thread;

    public ImportFolderWatcher(Path folder, TransactionService txService, Supplier<List<String>> accountIds,
                               String defaultAccountId, Consumer<Outcome> listener) {
        this.folder = folder;
        this.txService = txService;
        this.accountIds = accountIds;
        this.defaultAccountId = defaultAccountId;
        this.listener = listener;
    }

    public Path getFolder() {
        return folder;
    }

    /** Imports every CSV already in the folder that has not been processed yet. */
    public List<Outcome> scanOnce() throws IOException {
        Files.createDirectories(folder);
        List<Outcome> out = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.{csv,CSV}")) {
            for (Path f : files) {
                processFile(f).ifPresent(out::add);
            }
        }
        return out;
    }

    /** Imports one file unless this exact version (name, size, time) was already processed. */
    public synchronized Optional<Outcome> processFile(Path f) {
        try {
            String key = f.getFileName() + "|" + Files.size(f) + "|" + Files.getLastModifiedTime(f).toMillis();
            if (!processed.add(key)) {
                return Optional.empty();
            }
            Optional<ColumnMapping> profile = txService.detectProfile(f);
            Outcome o;
            if (profile.isEmpty()) {
                o = new Outcome(f, false, f.getFileName() + ": no saved bank format matches its columns. "
                        + "Import it once by hand and save a mapping.");
            } else {
                String account = accountFor(f);
                ImportResult r = txService.importFrom(f, account, profile.get().profileName());
                o = new Outcome(f, true, f.getFileName() + " → " + account + " (" + profile.get().profileName()
                        + "): " + r);
            }
            listener.accept(o);
            return Optional.of(o);
        } catch (IOException | RuntimeException e) {
            Outcome o = new Outcome(f, false, f.getFileName() + ": " + e.getMessage());
            listener.accept(o);
            return Optional.of(o);
        }
    }

    /** Uses an account id that appears in the file name (e.g. "visa_oct.csv" → visa), else the default. */
    String accountFor(Path f) {
        String name = f.getFileName().toString().toLowerCase();
        return accountIds.get().stream()
                .filter(id -> name.contains(id.toLowerCase()))
                .max(java.util.Comparator.comparingInt(String::length))
                .orElse(defaultAccountId);
    }

    /** Starts watching in a background thread. Existing files are imported first. */
    public void start() throws IOException {
        scanOnce();
        watchService = folder.getFileSystem().newWatchService();
        folder.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
        thread = new Thread(this::loop, "maplecfo-import-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    private void loop() {
        try {
            while (true) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> ev : key.pollEvents()) {
                    if (ev.context() instanceof Path p && p.toString().toLowerCase().endsWith(".csv")) {
                        Path f = folder.resolve(p);
                        if (waitUntilStable(f)) {
                            processFile(f);
                        }
                    }
                }
                if (!key.reset()) {
                    return;
                }
            }
        } catch (InterruptedException | ClosedWatchServiceException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** A browser may still be writing the download: wait until the size stops changing. */
    static boolean waitUntilStable(Path f) throws InterruptedException {
        long last = -1;
        for (int i = 0; i < 25; i++) {
            try {
                long size = Files.size(f);
                if (size > 0 && size == last) {
                    return true;
                }
                last = size;
            } catch (IOException e) {
                return false;
            }
            Thread.sleep(STABLE_WAIT_MS);
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        if (watchService != null) {
            watchService.close();
        }
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(5_000); // let a running import finish before the database is closed
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
