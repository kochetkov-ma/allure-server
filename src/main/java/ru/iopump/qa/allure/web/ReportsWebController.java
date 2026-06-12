package ru.iopump.qa.allure.web;

import io.qameta.allure.entity.ExecutorInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.iopump.qa.allure.entity.ReportEntity;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.allure.service.JpaReportService;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static ru.iopump.qa.allure.helper.Util.url;

/**
 * Server-rendered admin page for Allure reports at {@code /app/reports}.
 * <p>
 * The upload path is user-provided, deletion is guarded by a confirmation dialog, and
 * upload failures surface the {@link org.springframework.http.ProblemDetail#getDetail()}
 * as a toast via flash attributes.
 */
@Controller
@RequestMapping("/app/reports")
@RequiredArgsConstructor
@Validated
@Slf4j
public class ReportsWebController {

    private static final String VIEW_INDEX = "reports/index";
    private static final String REDIRECT_INDEX = "redirect:/app/reports";

    private final JpaReportService reportService;
    private final AllureProperties allureProperties;

    @GetMapping
    public String index(Model model) {
        populate(model);
        model.addAttribute("title", "Reports");
        model.addAttribute("activeNav", "reports");
        return VIEW_INDEX;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @CacheEvict(value = "reports", allEntries = true)
    public String upload(@RequestParam("path") String rawPath,
                         @RequestPart("file") MultipartFile file,
                         RedirectAttributes flash) {
        final String normalizedPath;
        try {
            normalizedPath = normalizePath(rawPath);
            requireNonEmptyZip(file);
        } catch (ResponseStatusException ex) {
            flash.addFlashAttribute("flash", toastMap("error", ex.getReason()));
            return REDIRECT_INDEX;
        }

        try {
            final ReportEntity created = reportService.uploadReport(
                normalizedPath,
                file.getInputStream(),
                new ExecutorInfo(),
                url(allureProperties)
            );
            flash.addFlashAttribute("flash",
                toastMap("success", "Report '" + created.getUuid() + "' uploaded under '" + normalizedPath + "'"));
            log.info("Report '{}' uploaded under '{}' via /app/reports", created.getUuid(), normalizedPath);
        } catch (IllegalArgumentException ex) {
            log.warn("Upload rejected: {}", ex.getMessage());
            flash.addFlashAttribute("flash", toastMap("error", ex.getMessage()));
        } catch (IOException ex) {
            log.error("Upload failed: {}", ex.getMessage(), ex);
            flash.addFlashAttribute("flash", toastMap("error", "Upload failed: " + ex.getMessage()));
        } catch (Exception ex) {
            log.error("Upload failed: {}", ex.getMessage(), ex);
            flash.addFlashAttribute("flash", toastMap("error", "Upload failed: " + ex.getMessage()));
        }
        return REDIRECT_INDEX;
    }

    @DeleteMapping("/{uuid}")
    @CacheEvict(value = "reports", allEntries = true)
    public String delete(@PathVariable("uuid") @NotBlank String uuid, RedirectAttributes flash) {
        try {
            reportService.deleteByUuid(uuid);
            flash.addFlashAttribute("flash", toastMap("success", "Report '" + uuid + "' deleted"));
            log.info("Report '{}' deleted via /app/reports", uuid);
        } catch (ResponseStatusException ex) {
            final HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
            final String reason = ex.getReason() == null
                ? (status == null ? "Delete failed" : status.getReasonPhrase())
                : ex.getReason();
            log.warn("Delete of '{}' rejected: {}", uuid, reason);
            flash.addFlashAttribute("flash", toastMap("error", reason));
        } catch (IllegalArgumentException ex) {
            log.warn("Delete of '{}' rejected: {}", uuid, ex.getMessage());
            flash.addFlashAttribute("flash", toastMap("error", "Invalid UUID: " + ex.getMessage()));
        } catch (IOException ex) {
            log.error("Delete of '{}' failed: {}", uuid, ex.getMessage(), ex);
            flash.addFlashAttribute("flash", toastMap("error", "Delete failed: " + ex.getMessage()));
        }
        return REDIRECT_INDEX;
    }

    @PostMapping("/bulk-delete")
    @CacheEvict(value = "reports", allEntries = true)
    public String bulkDelete(@Valid @NotEmpty @RequestParam("uuids") List<UUID> uuids, RedirectAttributes flash) {
        int deleted = 0;
        int failed = 0;
        for (UUID uuid : uuids) {
            try {
                reportService.deleteByUuid(uuid.toString());
                deleted++;
            } catch (ResponseStatusException ex) {
                failed++;
                log.warn("Bulk-delete: report '{}' skipped: {}", uuid, ex.getReason());
            } catch (IOException | IllegalArgumentException ex) {
                failed++;
                log.error("Bulk-delete: report '{}' failed: {}", uuid, ex.getMessage(), ex);
            }
        }
        final String level = failed == 0 ? "success" : (deleted == 0 ? "error" : "warning");
        final String msg = failed == 0
            ? "Deleted " + deleted + " report(s)"
            : "Deleted " + deleted + " report(s), " + failed + " failed";
        flash.addFlashAttribute("flash", toastMap(level, msg));
        log.info("Bulk-delete on /app/reports: deleted={}, failed={}", deleted, failed);
        return REDIRECT_INDEX;
    }

    ///// PRIVATE /////

    private void populate(Model model) {
        final List<ReportRow> rows = loadRows();
        final long totalSize = rows.stream().mapToLong(ReportRow::sizeBytes).sum();

        model.addAttribute("rows", rows);
        model.addAttribute("totalCount", rows.size());
        model.addAttribute("totalSizeDisplay", HumanSize.format(totalSize));
    }

    /**
     * Load all reports, newest first. Filtering and sorting are performed entirely
     * client-side (Alpine) in {@code reports/index.jte}; the server only fixes the
     * initial chronological order.
     */
    private List<ReportRow> loadRows() {
        final Collection<ReportEntity> entities = reportService.getAll();
        final String baseUrl = url(allureProperties);
        final String reportsDir = allureProperties.reports().dir();

        final Comparator<ReportEntity> byCreatedDesc =
            Comparator.comparing(ReportEntity::getCreatedDateTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed();

        final Stream<ReportEntity> stream = entities.stream().sorted(byCreatedDesc);

        final Path reportsRoot = allureProperties.reports().dirPath();

        return stream.map(e -> {
            final long sizeBytes = directorySize(reportsRoot.resolve(e.getUuid().toString()));
            final String rawBuildUrl = e.getBuildUrl();
            return ReportRow.from(
                e.getUuid().toString(),
                e.getPath(),
                formatCreated(e),
                createdEpoch(e),
                e.generateUrl(baseUrl, reportsDir),
                sizeBytes,
                HumanSize.format(sizeBytes),
                rawBuildUrl,
                buildLabel(rawBuildUrl)
            );
        }).toList();
    }

    /** Recursive byte count for a report directory; returns 0 when the path is missing or unreadable. */
    private static long directorySize(Path dir) {
        final java.io.File file = dir.toFile();
        if (!file.isDirectory()) {
            return 0L;
        }
        try {
            return FileUtils.sizeOfDirectory(file);
        } catch (IllegalArgumentException ex) {
            log.warn("Unable to size report directory '{}': {}", dir, ex.getMessage());
            return 0L;
        }
    }

    /**
     * Render the last two non-empty path segments of a CI build URL (e.g. {@code jobs/12345}
     * or {@code merge_requests/42}) so the grid keeps a recognizable, compact label while the
     * full URL stays behind the link. Falls back to host, then to the raw string.
     */
    static String buildLabel(String raw) {
        if (raw == null) {
            return "";
        }
        final String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        try {
            final URI uri = URI.create(trimmed);
            final String p = uri.getPath();
            final List<String> segments = (p == null || p.isEmpty())
                ? List.of()
                : Arrays.stream(p.split("/")).filter(s -> !s.isEmpty()).toList();
            if (segments.isEmpty()) {
                return uri.getHost() == null ? trimmed : uri.getHost();
            }
            if (segments.size() == 1) {
                return segments.get(0);
            }
            return segments.get(segments.size() - 2) + "/" + segments.get(segments.size() - 1);
        } catch (IllegalArgumentException ex) {
            return trimmed;
        }
    }

    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private static String formatCreated(ReportEntity entity) {
        if (entity.getCreatedDateTime() == null) {
            return "";
        }
        // ReportEntity.getCreatedDateTime() is already stored as UTC LocalDateTime.
        // Format once server-side so every viewer sees identical text regardless of browser timezone.
        return entity.getCreatedDateTime().format(DISPLAY_DATE_FORMAT);
    }

    /**
     * Epoch milliseconds of the report creation instant for chronological (numeric) client-side
     * sorting. The dd.MM.yyyy display string is not chronologically sortable; this raw value is.
     * Returns {@code 0} when the creation time is unknown.
     */
    private static long createdEpoch(ReportEntity entity) {
        if (entity.getCreatedDateTime() == null) {
            return 0L;
        }
        return entity.getCreatedDateTime().toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    /**
     * Trim input path and reject empty values or leading slashes. The REST API uses
     * {@code POST /api/report/{reportPath}} where the path becomes a URL segment;
     * leading slashes would produce an ambiguous URL.
     */
    private static String normalizePath(String rawPath) {
        final String trimmed = rawPath == null ? "" : rawPath.trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path must not be blank");
        }
        if (trimmed.startsWith("/") || trimmed.startsWith("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path must not start with a slash");
        }
        return trimmed;
    }

    private static void requireNonEmptyZip(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must be a non-empty multipart upload");
        }
        final String originalFilename = file.getOriginalFilename();
        if (StringUtils.isNotBlank(originalFilename) && !originalFilename.endsWith(".zip")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "File must have '.zip' extension but was '" + originalFilename + "'");
        }
    }

    private static Map<String, String> toastMap(String level, String message) {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put("level", level);
        map.put("message", message == null ? "" : message);
        return map;
    }
}
