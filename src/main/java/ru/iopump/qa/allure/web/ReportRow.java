package ru.iopump.qa.allure.web;

/**
 * View model for a single row of the Reports admin grid at {@code /app/reports}.
 * <p>
 * All values are pre-rendered to strings server-side so the JTE template needs no
 * additional formatting logic. {@code created} is a {@link java.time.LocalDateTime}
 * formatted as {@code dd.MM.yyyy HH:mm:ss} at a fixed UTC offset — every viewer sees
 * the same text regardless of browser timezone. That display string is <strong>not</strong>
 * chronologically sortable (day-of-month dominates a lexicographic compare), so
 * {@code createdEpoch} carries the underlying creation instant in epoch milliseconds
 * for the client-side numeric column sort. {@code sizeBytes} is the raw byte count of
 * the report directory on disk (for numeric client-side sorting) and {@code sizeDisplay}
 * is the human-readable rendering (e.g. {@code "12.3 MB"}). {@code buildUrl} is the full
 * CI build URL as captured on upload (may be empty); {@code buildLabel} is a shortened
 * rendering (last two URL path segments) for display.
 */
public record ReportRow(
    String uuid,
    String uuidShort,
    String path,
    String created,
    long createdEpoch,
    String url,
    long sizeBytes,
    String sizeDisplay,
    String buildUrl,
    String buildLabel,
    boolean active,
    String latestUrl
) {

    public static ReportRow from(String uuid,
                                 String path,
                                 String created,
                                 long createdEpoch,
                                 String url,
                                 long sizeBytes,
                                 String sizeDisplay,
                                 String buildUrl,
                                 String buildLabel,
                                 boolean active,
                                 String latestUrl) {
        final String shortId = uuid == null || uuid.length() < 8 ? uuid : uuid.substring(0, 8) + "…";
        return new ReportRow(uuid, shortId, path, created, createdEpoch, url, sizeBytes, sizeDisplay,
            buildUrl == null ? "" : buildUrl,
            buildLabel == null ? "" : buildLabel,
            active,
            latestUrl == null ? "" : latestUrl);
    }
}
