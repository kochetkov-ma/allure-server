package ru.iopump.qa.allure.web.dto;

/**
 * Row projection rendered by the {@code /app/results} JTE grid.
 * <p>
 * {@code createdAt} is formatted server-side as {@code dd.MM.yyyy HH:mm:ss} at a fixed
 * UTC offset — every viewer sees identical text regardless of the browser timezone.
 * That display string is <strong>not</strong> chronologically sortable (day-of-month
 * dominates a lexicographic compare), so {@code createdEpoch} carries the underlying
 * creation instant in epoch milliseconds for both the server-side comparator and the
 * client-side numeric column sort. {@code sizeBytes} is the raw byte count used for
 * numeric client-side sorting; {@code sizeDisplay} is the human-readable rendering
 * ({@code "12.3 MB"}).
 *
 * @param uuid         result storage directory UUID
 * @param sizeBytes    total bytes on disk for the result directory
 * @param sizeDisplay  human-readable size (e.g. {@code "12.3 MB"})
 * @param createdAt    pre-formatted UTC timestamp string; empty when the creation time cannot be read
 * @param createdEpoch creation time in epoch milliseconds for chronological sorting; {@code 0} when unknown
 */
public record ResultRow(String uuid, long sizeBytes, String sizeDisplay, String createdAt, long createdEpoch) {
}
