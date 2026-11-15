package com.ronial.browser.service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class BrowsingHistory implements AutoCloseable {
    private static final int MAX_ENTRIES = 500;
    private static final System.Logger LOGGER = System.getLogger(BrowsingHistory.class.getName());

    public record Entry(String title, String url, Instant visitedAt) {}

    private final Path path;
    private final ArrayList<Entry> entries = new ArrayList<>();
    private boolean dirty;
    private boolean saveScheduled;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "browser-history-writer");
        thread.setDaemon(true);
        return thread;
    });

    public BrowsingHistory() {
        this(Path.of(System.getProperty("lab4.history",
                Path.of(System.getProperty("user.home"), ".ronial-browser", "history.tsv").toString())));
    }

    public BrowsingHistory(Path path) {
        this.path = path.toAbsolutePath();
        load();
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    public synchronized void record(String title, String url) {
        if (writer.isShutdown() || !isWebUrl(url)) return;
        entries.add(0, new Entry(title == null || title.isBlank() ? url : title, url,
                Instant.ofEpochMilli(System.currentTimeMillis())));
        if (entries.size() > MAX_ENTRIES) entries.remove(MAX_ENTRIES);
        scheduleSave();
    }

    public synchronized void clear() {
        if (writer.isShutdown()) return;
        entries.clear();
        scheduleSave();
    }

    private void load() {
        if (Files.notExists(path)) return;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.ISO_8859_1)) {
            String line;
            while (entries.size() < MAX_ENTRIES && (line = reader.readLine()) != null) {
                String[] fields = line.split("\\t", -1);
                if (fields.length != 3) continue;
                try {
                    Instant visitedAt = Instant.ofEpochMilli(Long.parseLong(fields[0]));
                    String title = decode(fields[1]);
                    String url = decode(fields[2]);
                    if (isWebUrl(url)) entries.add(new Entry(title.isBlank() ? url : title, url, visitedAt));
                } catch (IllegalArgumentException malformedEntry) {
                    // A damaged row should not hide other visits.
                }
            }
        } catch (IOException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not read browsing history", exception);
        }
    }

    private void scheduleSave() {
        dirty = true;
        if (saveScheduled) return;
        saveScheduled = true;
        writer.execute(this::savePending);
    }

    private void savePending() {
        while (true) {
            List<Entry> snapshot;
            synchronized (this) {
                if (!dirty) {
                    saveScheduled = false;
                    return;
                }
                dirty = false;
                snapshot = List.copyOf(entries);
            }
            try {
                write(snapshot);
            } catch (IOException exception) {
                LOGGER.log(System.Logger.Level.WARNING, "Could not save browsing history", exception);
            }
        }
    }

    private void write(List<Entry> snapshot) throws IOException {
        Path directory = Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(directory, "history-", ".tmp");
        try {
            try (var output = Files.newBufferedWriter(temporary)) {
                for (Entry entry : snapshot) {
                    output.write(entry.visitedAt().toEpochMilli() + "\t"
                            + encode(entry.title()) + "\t" + encode(entry.url()));
                    output.newLine();
                }
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static boolean isWebUrl(String url) {
        if (url == null) return false;
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return uri.getHost() != null && uri.getRawUserInfo() == null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        synchronized (this) {
            writer.shutdown();
        }
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.log(System.Logger.Level.WARNING, "Timed out saving browsing history");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
