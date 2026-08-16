package ru.iopump.qa.allure.web;

import lombok.experimental.UtilityClass;

import java.util.Locale;

/**
 * Format a byte count as a human-readable IEC string (e.g. {@code "12.3 MB"}).
 * <p>
 * Uses binary units (1 KB = 1024 B) with one decimal place, matching what users
 * already see from {@link org.apache.commons.io.FileUtils#byteCountToDisplaySize(long)}
 * but with decimal precision (commons-io truncates to integer).
 */
@UtilityClass
public class HumanSize {

    private static final long KB = 1024L;
    private static final long MB = KB * 1024L;
    private static final long GB = MB * 1024L;
    private static final long TB = GB * 1024L;

    public static String format(long bytes) {
        if (bytes < 0L) {
            return "0 B";
        }
        if (bytes < KB) {
            return bytes + " B";
        }
        if (bytes < MB) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / (double) KB);
        }
        if (bytes < GB) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (double) MB);
        }
        if (bytes < TB) {
            return String.format(Locale.ROOT, "%.1f GB", bytes / (double) GB);
        }
        return String.format(Locale.ROOT, "%.1f TB", bytes / (double) TB);
    }
}
