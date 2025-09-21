package dev.abdelrahman.rankcorex.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtils {

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)(s|m|h|d|mo|y)");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss");

    /**
     * Parse time string like "7d", "30m", "1y" into seconds
     * @param timeStr the time string to parse
     * @return seconds, or -1 for permanent/invalid
     */
    public static long parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return -1; // permanent
        }

        Matcher matcher = TIME_PATTERN.matcher(timeStr.toLowerCase());
        if (!matcher.matches()) {
            return -1; // invalid format = permanent
        }

        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        switch (unit) {
            case "s": return amount;
            case "m": return amount * 60;
            case "h": return amount * 3600;
            case "d": return amount * 86400;
            case "mo": return amount * 2592000; // 30 days
            case "y": return amount * 31536000; // 365 days
            default: return -1;
        }
    }

    /**
     * Get current timestamp as string
     */
    public static String getCurrentTimestamp() {
        return LocalDateTime.now().format(FORMATTER);
    }

    /**
     * Get future timestamp by adding seconds
     */
    public static String getFutureTimestamp(long seconds) {
        if (seconds <= 0) {
            return null; // permanent
        }
        return LocalDateTime.now().plusSeconds(seconds).format(FORMATTER);
    }

    /**
     * Check if timestamp has expired
     */
    public static boolean hasExpired(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return false; // permanent ranks don't expire
        }

        try {
            LocalDateTime expiry = LocalDateTime.parse(timestamp, FORMATTER);
            return LocalDateTime.now().isAfter(expiry);
        } catch (Exception e) {
            return true; // if we can't parse, assume expired
        }
    }

    /**
     * Get remaining time until expiry in a readable format
     */
    public static String getTimeRemaining(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return "Permanent";
        }

        try {
            LocalDateTime expiry = LocalDateTime.parse(timestamp, FORMATTER);
            LocalDateTime now = LocalDateTime.now();

            if (now.isAfter(expiry)) {
                return "Expired";
            }

            long days = ChronoUnit.DAYS.between(now, expiry);
            long hours = ChronoUnit.HOURS.between(now, expiry) % 24;
            long minutes = ChronoUnit.MINUTES.between(now, expiry) % 60;

            if (days > 0) {
                return days + "d " + hours + "h " + minutes + "m";
            } else if (hours > 0) {
                return hours + "h " + minutes + "m";
            } else {
                return minutes + "m";
            }

        } catch (Exception e) {
            return "Invalid";
        }
    }

    /**
     * Get time since given in readable format
     */
    public static String getTimeSince(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return "Unknown";
        }

        try {
            LocalDateTime given = LocalDateTime.parse(timestamp, FORMATTER);
            LocalDateTime now = LocalDateTime.now();

            long days = ChronoUnit.DAYS.between(given, now);
            long hours = ChronoUnit.HOURS.between(given, now) % 24;
            long minutes = ChronoUnit.MINUTES.between(given, now) % 60;

            if (days > 0) {
                return days + "d " + hours + "h ago";
            } else if (hours > 0) {
                return hours + "h " + minutes + "m ago";
            } else {
                return minutes + "m ago";
            }

        } catch (Exception e) {
            return "Unknown";
        }
    }
}