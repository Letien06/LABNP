package com.ronial.app.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtils {
    public static String formatInstant(Instant instant) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss - dd/MM/yyyy");
        return ZonedDateTime.ofInstant(instant, ZoneId.of("Asia/Ho_Chi_Minh")).format(formatter);
    }
}
