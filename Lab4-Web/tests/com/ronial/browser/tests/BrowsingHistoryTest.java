package com.ronial.browser.tests;

import com.ronial.browser.service.BrowsingHistory;
import com.ronial.browser.service.BrowsingHistory.Entry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class BrowsingHistoryTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory(Path.of("target"), "browser-history-");
        Path file = directory.resolve("history.tsv");
        String firstUrl = "https://example.test/first";
        String secondUrl = "https://example.test/second";
        String firstTitle = "Tiêu đề\ttrang\nweb";
        try {
            List<Entry> recorded;
            try (BrowsingHistory history = new BrowsingHistory(file)) {
                history.record(firstTitle, firstUrl);
                history.record(null, secondUrl);
                history.record("Discard", "about:blank");
                recorded = history.entries();
            }
            check(recorded.stream().map(Entry::url).toList().equals(List.of(secondUrl, firstUrl)), "Latest visits first");
            Files.writeString(file, "broken\n" + Files.readString(file) + "0\t!\t!\n");
            try (BrowsingHistory history = new BrowsingHistory(file)) {
                List<Entry> loaded = history.entries();
                check(loaded.size() == 2, "Skip damaged rows");
                check(loaded.get(0).title().equals(secondUrl), "URL fallback for missing title");
                check(loaded.get(1).title().equals(firstTitle), "Preserve Unicode and control characters in titles");
                check(loaded.get(1).visitedAt().equals(recorded.get(1).visitedAt()), "Preserve the visit timestamp");
                for (int index = 0; index < 510; index++) history.record("Page", firstUrl + "/" + index);
            }
            try (BrowsingHistory history = new BrowsingHistory(file)) {
                check(history.entries().size() == 500, "Limit persisted history size");
                check(history.entries().get(0).url().equals(firstUrl + "/509"), "Retain the newest visits");
                history.clear();
            }
            try (BrowsingHistory history = new BrowsingHistory(file)) {
                check(history.entries().isEmpty(), "Clearing history persists across restarts");
            }
        } finally {
            Files.deleteIfExists(file);
            Files.delete(directory);
        }
        System.out.println("Browsing history checks PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
