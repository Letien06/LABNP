package com.ronial.browser.model;

import java.util.OptionalLong;

public record DownloadProgress(long bytesReceived, OptionalLong totalBytes) { }
