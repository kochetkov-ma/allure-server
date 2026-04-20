package ru.iopump.qa.allure.web.dto;

/**
 * Row projection rendered by the {@code /app/results} JTE grid.
 * <p>
 * {@code createdAt} is serialized as an ISO-8601 instant string so the browser-side
 * Alpine helper can convert it into the visitor's local timezone at render time.
 *
 * @param uuid      result storage directory UUID
 * @param sizeBytes total bytes on disk for the result directory
 * @param createdAt ISO-8601 {@code Instant} string (e.g. {@code 2026-04-20T07:53:57Z}); empty when the creation time cannot be read
 */
public record ResultRow(String uuid, long sizeBytes, String createdAt) {
}
