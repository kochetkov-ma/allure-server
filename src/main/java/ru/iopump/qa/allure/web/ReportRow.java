package ru.iopump.qa.allure.web;

/**
 * View model for a single row of the Reports admin grid at {@code /app/reports}.
 * <p>
 * Values are pre-rendered to strings so the JTE template does not need any
 * additional formatting logic. {@code created} is serialised as an ISO-8601
 * instant string so the client-side Alpine snippet can feed it to
 * {@code new Date(...)} and render it in the browser's local timezone.
 */
public record ReportRow(
    String uuid,
    String uuidShort,
    String path,
    String created,
    String url
) {

    public static ReportRow from(String uuid, String path, String createdIso, String url) {
        final String shortId = uuid == null || uuid.length() < 8 ? uuid : uuid.substring(0, 8) + "…";
        return new ReportRow(uuid, shortId, path, createdIso, url);
    }
}
