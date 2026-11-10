package com.ronial.app.utils;

public class StringUtils {
    public static String truncateString(String input, int maxLength) {
        if (input == null || input.length() <= maxLength) {
            return input;
        }

        return input.substring(0, maxLength) + "...";
    }

    // Phương thức chuyển đổi emoji trong HTML thành mã Unicode entity
    public static String convertHtmlEmojisToUnicode(String input) {
        StringBuilder result = new StringBuilder();

        // Duyệt qua tất cả các ký tự trong HTML
        for (char c : input.toCharArray()) {
            // Nếu là ký tự emoji, chuyển thành mã Unicode entity
            if (Character.isSurrogate(c) || isEmoji(c)) {
                String hexCode = Integer.toHexString(c).toUpperCase();
                result.append("&#x").append(hexCode).append(";");
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    // Kiểm tra xem ký tự có phải là emoji không
    public static boolean isEmoji(char c) {
        // Kiểm tra ký tự có thuộc vào các Unicode block chứa emoji không
        return (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.EMOTICONS) ||
                (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS) ||
                (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS) ||
                (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.TRANSPORT_AND_MAP_SYMBOLS) ||
                (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.SUPPLEMENTARY_PRIVATE_USE_AREA_B);
    }
}
