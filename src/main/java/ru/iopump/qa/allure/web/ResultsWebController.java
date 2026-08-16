package ru.iopump.qa.allure.web;

import io.qameta.allure.entity.ExecutorInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.iopump.qa.allure.entity.ReportEntity;
import ru.iopump.qa.allure.properties.AllureProperties;
import ru.iopump.qa.allure.service.JpaReportService;
import ru.iopump.qa.allure.service.PathUtil;
import ru.iopump.qa.allure.service.ResultService;
import ru.iopump.qa.allure.web.dto.GenerateForm;
import ru.iopump.qa.allure.web.dto.ResultRow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static ru.iopump.qa.allure.helper.Util.url;

/**
 * Admin web surface for Allure results.
 * <p>
 * Renders JTE templates under {@code src/main/jte/results/} and handles the
 * upload / generate / delete form posts driven by htmx+Alpine. REST contracts
 * remain under {@code /api/result} (see {@link ru.iopump.qa.allure.controller.AllureResultController})
 * and {@code /api/report} (see {@link ru.iopump.qa.allure.controller.AllureReportController}) —
 * this controller is MVC-only and never returns JSON. The "Generate report" dialog
 * exposes the full {@link ExecutorInfo} contract (name/type/url + build* + report*).
 */
@Controller
@Validated
@RequestMapping("/app/results")
@RequiredArgsConstructor
@Slf4j
public class ResultsWebController {

    private static final String FLASH_LEVEL = "level";
    private static final String FLASH_MESSAGE = "message";
    private static final String LEVEL_SUCCESS = "success";
    private static final String LEVEL_ERROR = "error";
    private static final String REDIRECT_SELF = "redirect:/app/results";
    private static final String ZIP_MIME = "application/zip";
    private static final String ZIP_MIME_X = "application/x-zip-compressed";

    private final ResultService resultService;
    private final JpaReportService reportService;
    private final AllureProperties allureProperties;

    /** Results admin page — full layout with upload form, grid, dialogs. */
    @GetMapping
    public String index(Model model) throws IOException {
        final List<ResultRow> rows = loadRows();
        populateTotals(model, rows);
        model.addAttribute("rows", rows);
        return "results/index";
    }

    private static void populateTotals(Model model, List<ResultRow> rows) {
        final long totalSize = rows.stream().mapToLong(ResultRow::sizeBytes).sum();
        model.addAttribute("totalCount", rows.size());
        model.addAttribute("totalSizeDisplay", HumanSize.format(totalSize));
    }

    /** Multipart upload of {@code allure-results.zip}; delegates to {@link ResultService#unzipAndStore}. */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @CacheEvict(value = "results", allEntries = true)
    public String upload(@RequestPart("file") MultipartFile file, RedirectAttributes flash) {
        try {
            requireNonEmptyZip(file);
            final Path stored = resultService.unzipAndStore(file.getInputStream());
            final String uuid = stored.getFileName().toString();
            log.info("Result '{}' uploaded via /app/results (filename='{}')", uuid, file.getOriginalFilename());
            addFlash(flash, LEVEL_SUCCESS, "Result uploaded: " + uuid);
        } catch (ResponseStatusException e) {
            log.warn("Upload rejected: {}", e.getReason());
            addFlash(flash, LEVEL_ERROR, e.getReason());
        } catch (Exception e) {
            log.error("Upload failed", e);
            addFlash(flash, LEVEL_ERROR, "Upload failed: " + e.getLocalizedMessage());
        }
        return REDIRECT_SELF;
    }

    /**
     * Generate a report from the selected result UUIDs. Translates the full UI form
     * (including the extended {@link ExecutorInfo} fields) into a single
     * {@link JpaReportService#generate} call — REST endpoints stay untouched.
     */
    @PostMapping("/generate")
    @CacheEvict(value = {"reports", "results"}, allEntries = true)
    public String generate(@Valid @ModelAttribute("form") GenerateForm form,
                           BindingResult errors,
                           RedirectAttributes flash) {
        if (errors.hasErrors()) {
            final String summary = summarizeErrors(errors);
            log.warn("Generate form rejected: {}", summary);
            addFlash(flash, LEVEL_ERROR, "Generate rejected: " + summary);
            return REDIRECT_SELF;
        }

        try {
            final ExecutorInfo executorInfo = buildExecutorInfo(form);
            final String reportPath = String.join("/", form.reportPath());
            final List<Path> resultDirs = form.resultUuids().stream()
                .map(uuid -> resultService.getStoragePath().resolve(uuid))
                .collect(Collectors.toUnmodifiableList());

            final ReportEntity entity = reportService.generate(
                reportPath,
                resultDirs,
                form.deleteResults(),
                executorInfo,
                url(allureProperties)
            );
            log.info("Report '{}' generated via /app/results (path='{}', results={})",
                entity.getUuid(), reportPath, form.resultUuids().size());
            addFlash(flash, LEVEL_SUCCESS, "Report generated: " + entity.getUuid());
        } catch (Exception e) {
            log.error("Report generation failed", e);
            addFlash(flash, LEVEL_ERROR, "Report generation failed: " + e.getLocalizedMessage());
        }
        return REDIRECT_SELF;
    }

    /** Deletes a single result directory by UUID; enforces the project UUID regex. */
    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.SEE_OTHER)
    @CacheEvict(value = "results", allEntries = true)
    public String delete(@PathVariable @NotBlank @Pattern(regexp = PathUtil.UUID_PATTERN) String uuid,
                         RedirectAttributes flash) {
        try {
            resultService.internalDeleteByUUID(uuid);
            log.info("Result '{}' deleted via /app/results", uuid);
            addFlash(flash, LEVEL_SUCCESS, "Result deleted: " + uuid);
        } catch (Exception e) {
            log.error("Delete failed for '{}'", uuid, e);
            addFlash(flash, LEVEL_ERROR, "Delete failed: " + e.getLocalizedMessage());
        }
        return REDIRECT_SELF;
    }

    /** Bulk-delete results. Best-effort: failures on individual UUIDs are logged, not fatal. */
    @PostMapping("/bulk-delete")
    @CacheEvict(value = "results", allEntries = true)
    public String bulkDelete(@Valid @NotEmpty @RequestParam("uuids") List<UUID> uuids, RedirectAttributes flash) {
        int deleted = 0;
        int failed = 0;
        for (UUID uuid : uuids) {
            try {
                resultService.internalDeleteByUUID(uuid.toString());
                deleted++;
            } catch (Exception e) {
                failed++;
                log.error("Bulk-delete: result '{}' failed: {}", uuid, e.getMessage(), e);
            }
        }
        final String level = failed == 0 ? LEVEL_SUCCESS : (deleted == 0 ? LEVEL_ERROR : "warning");
        final String msg = failed == 0
            ? "Deleted " + deleted + " result(s)"
            : "Deleted " + deleted + " result(s), " + failed + " failed";
        addFlash(flash, level, msg);
        log.info("Bulk-delete on /app/results: deleted={}, failed={}", deleted, failed);
        return REDIRECT_SELF;
    }

    //// PRIVATE ////

    /**
     * Load all results, newest first. Filtering and sorting are performed entirely
     * client-side (Alpine) in {@code results/index.jte}; the server only fixes the
     * initial chronological order.
     */
    private List<ResultRow> loadRows() throws IOException {
        final Collection<Path> all = resultService.getAll();
        return all.stream()
            .map(this::toRow)
            .sorted(Comparator.comparingLong(ResultRow::createdEpoch).reversed())
            .collect(Collectors.toUnmodifiableList());
    }

    private static final String DEFAULT_DATE_PATTERN = "dd.MM.yyyy HH:mm:ss";
    private DateTimeFormatter displayDateFormat;

    /**
     * Created-column formatter derived from {@code allure.date-format}; falls back to the default
     * UTC pattern when the property is blank or invalid. Resolved once and cached.
     */
    private DateTimeFormatter displayDateFormat() {
        if (displayDateFormat == null) {
            final String pattern = allureProperties.dateFormat();
            try {
                displayDateFormat = DateTimeFormatter.ofPattern(
                    StringUtils.isBlank(pattern) ? DEFAULT_DATE_PATTERN : pattern);
            } catch (IllegalArgumentException ex) {
                log.warn("Invalid allure.date-format '{}', using '{}'", pattern, DEFAULT_DATE_PATTERN);
                displayDateFormat = DateTimeFormatter.ofPattern(DEFAULT_DATE_PATTERN);
            }
        }
        return displayDateFormat;
    }

    private ResultRow toRow(Path dir) {
        final long bytes = directorySize(dir);
        String createdAt = "";
        long createdEpoch = 0L;
        try {
            final BasicFileAttributes attr = Files.readAttributes(dir, BasicFileAttributes.class);
            // Format once server-side at fixed UTC so every viewer sees identical text,
            // but keep the raw epoch for chronological (numeric) sorting.
            createdEpoch = attr.creationTime().toMillis();
            final LocalDateTime utc = LocalDateTime.ofInstant(attr.creationTime().toInstant(), ZoneOffset.UTC);
            createdAt = utc.format(displayDateFormat());
        } catch (IOException e) {
            log.error("Unable to read creation time for '{}'", dir, e);
        }
        return new ResultRow(dir.getFileName().toString(), bytes,
            ru.iopump.qa.allure.web.HumanSize.format(bytes), createdAt, createdEpoch);
    }

    private static final int SIZE_CACHE_MAX = 10_000;

    /**
     * Bounded LRU cache of recursively-computed result directory sizes, keyed by directory path
     * plus its last-modified time. Result directories are effectively immutable once extracted, so
     * a hit spares the recursive {@link FileUtils#sizeOfDirectory} walk on every /app/results
     * render as history grows. The last-modified key component self-invalidates an entry if the
     * directory is ever rewritten in place; the LRU bound caps memory for deleted/rotated dirs.
     */
    private static final Map<String, Long> SIZE_CACHE = Collections.synchronizedMap(
        new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > SIZE_CACHE_MAX;
            }
        });

    /** Recursive byte count for a result directory; returns 0 when the path is missing or unreadable. */
    private static long directorySize(Path dir) {
        final java.io.File file = dir.toFile();
        if (!file.isDirectory()) {
            return 0L;
        }
        final String key = dir.toAbsolutePath() + ":" + file.lastModified();
        final Long cached = SIZE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            final long bytes = FileUtils.sizeOfDirectory(file);
            SIZE_CACHE.put(key, bytes);
            return bytes;
        } catch (IllegalArgumentException ex) {
            log.warn("Unable to size result directory '{}': {}", dir, ex.getMessage());
            return 0L;
        }
    }

    /**
     * Build an {@link ExecutorInfo} from the form — {@code ExecutorInfo} is a mutable
     * Lombok {@code @Data} class with a no-arg constructor, so we set only the fields
     * the caller provided (blank strings are ignored to keep the stored {@code ci-executor.json}
     * minimal). {@link JpaReportService} fills in sensible defaults for blank name/type.
     */
    private static ExecutorInfo buildExecutorInfo(GenerateForm form) {
        final ExecutorInfo info = new ExecutorInfo();
        applyIfPresent(form.executorName(), info::setName);
        applyIfPresent(form.executorType(), info::setType);
        applyIfPresent(form.executorUrl(), info::setUrl);
        applyIfPresent(form.buildName(), info::setBuildName);
        applyIfPresent(form.buildUrl(), info::setBuildUrl);
        applyIfPresent(form.reportUrl(), info::setReportUrl);
        applyIfPresent(form.reportName(), info::setReportName);
        return info;
    }

    private static void applyIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (StringUtils.isNotBlank(value)) {
            setter.accept(value);
        }
    }

    private static void requireNonEmptyZip(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "File must be a non-empty multipart upload");
        }
        final String contentType = file.getContentType();
        if (StringUtils.isNotBlank(contentType)
            && !StringUtils.equalsAny(contentType, ZIP_MIME, ZIP_MIME_X)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Content-Type must be '" + ZIP_MIME + "' but was '" + contentType + "'");
        }
        final String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && !originalFilename.endsWith(".zip")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "File must have '.zip' extension but was '" + originalFilename + "'");
        }
    }

    private static String summarizeErrors(BindingResult errors) {
        return errors.getAllErrors().stream()
            .map(ResultsWebController::formatError)
            .collect(Collectors.joining("; "));
    }

    private static String formatError(ObjectError error) {
        return error.getObjectName() + ": " + StringUtils.defaultString(error.getDefaultMessage(), error.getCode());
    }

    private static void addFlash(RedirectAttributes flash, String level, String message) {
        flash.addFlashAttribute("flash", Map.of(FLASH_LEVEL, level, FLASH_MESSAGE, StringUtils.defaultString(message)));
    }
}
