package com.ronial.browser.model;

import java.nio.file.Path;

public record DownloadResult(Path path, long bytes) { }
