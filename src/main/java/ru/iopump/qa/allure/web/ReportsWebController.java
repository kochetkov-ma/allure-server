package ru.iopump.qa.allure.web;

import io.qameta.allure.entity.ExecutorInfo;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    static final String SORT_CREATED = "created";
    static final String SORT_PATH = "path";
    static final String SORT_UUID = "uuid";
    static final String DIR_ASC = "asc";
    static final String DIR_DESC = "desc";

    private static final String VIEW_INDEX = "reports/index";
    private static final String VIEW_GRID = "reports/grid";
    private static final String REDIRECT_INDEX = "redirect:/app/reports";

    private final JpaReportService reportService;
    private final AllureProperties allureProperties;

    @GetMapping
    public String index(@RequestParam(required = false) String q,
                        @RequestParam(defaultValue = SORT_CREATED) String sort,
                        @RequestParam(defaultValue = DIR_DESC) String dir,
                        Model model) {
        populate(model, q, sort, dir);
        model.addAttribute("title", "Reports");
        model.addAttribute("activeNav", "reports");
        return VIEW_INDEX;
    }

    @GetMapping("/grid")
    public String grid(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = SORT_CREATED) String sort,
                       @RequestParam(defaultValue = DIR_DESC) String dir,
                       Model model) {
        populate(model, q, sort, dir);
        model.addAttribute("oob", true);
        return VIEW_GRID;
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

    ///// PRIVATE /////

    private void populate(Model model, String q, String sort, String dir) {
        final String normalizedSort = normalizeSort(sort);
        final String normalizedDir = normalizeDir(dir);
        final String queryTrim = q == null ? "" : q.trim();

        model.addAttribute("rows", loadRows(queryTrim, normalizedSort, normalizedDir));
        model.addAttribute("q", queryTrim);
        model.addAttribute("sort", normalizedSort);
        model.addAttribute("dir", normalizedDir);
    }

    private List<ReportRow> loadRows(String queryTrim, String sort, String dir) {
        final Collection<ReportEntity> entities = reportService.getAll();
        final String baseUrl = url(allureProperties);
        final String reportsDir = allureProperties.reports().dir();

        final Comparator<ReportEntity> comparator = comparatorFor(sort);
        final Comparator<ReportEntity> effective = DIR_DESC.equals(dir) ? comparator.reversed() : comparator;

        Stream<ReportEntity> stream = entities.stream().sorted(effective);
        if (!queryTrim.isEmpty()) {
            final String needle = queryTrim.toLowerCase();
            stream = stream.filter(e -> matches(e, needle));
        }

        return stream.map(e -> ReportRow.from(
            e.getUuid().toString(),
            e.getPath(),
            toIsoUtc(e),
            e.generateUrl(baseUrl, reportsDir)
        )).toList();
    }

    private static Comparator<ReportEntity> comparatorFor(String sort) {
        return switch (sort) {
            case SORT_PATH -> Comparator.comparing(ReportEntity::getPath, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case SORT_UUID -> Comparator.comparing(e -> e.getUuid().toString());
            default -> Comparator.comparing(ReportEntity::getCreatedDateTime, Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private static boolean matches(ReportEntity entity, String needleLower) {
        final String path = entity.getPath();
        final String uuid = entity.getUuid() == null ? "" : entity.getUuid().toString();
        if (path != null && path.toLowerCase().contains(needleLower)) {
            return true;
        }
        return uuid.toLowerCase().contains(needleLower);
    }

    private static String toIsoUtc(ReportEntity entity) {
        if (entity.getCreatedDateTime() == null) {
            return "";
        }
        // ReportEntity.getCreatedDateTime() returns a LocalDateTime already normalised to UTC.
        return entity.getCreatedDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "Z";
    }

    private static String normalizeSort(String sort) {
        if (sort == null) {
            return SORT_CREATED;
        }
        return switch (sort) {
            case SORT_PATH, SORT_UUID, SORT_CREATED -> sort;
            default -> SORT_CREATED;
        };
    }

    private static String normalizeDir(String dir) {
        if (DIR_ASC.equalsIgnoreCase(dir)) {
            return DIR_ASC;
        }
        return DIR_DESC;
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
