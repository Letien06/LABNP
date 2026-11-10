package com.ronial.app.utils;

import java.util.regex.Pattern;

public class RegexUtils {
    private static final String EMAIL_REGEX= "^[a-zA-Z0-9._%+-]+@gmail.com";
    public static boolean isEmail(String email) {
        return Pattern.compile(EMAIL_REGEX).matcher(email).matches();
    }
}
