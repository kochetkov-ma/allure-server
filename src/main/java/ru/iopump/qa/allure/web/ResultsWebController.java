package ru.iopump.qa.allure.web;

import io.qameta.allure.entity.ExecutorInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    public String index(@RequestParam(name = "q", required = false) String query,
                        @RequestParam(name = "sort", defaultValue = "createdAt") String sort,
                        @RequestParam(name = "dir", defaultValue = "desc") String direction,
                        Model model) throws IOException {
        final String normalizedSort = normalizeSort(sort);
        final String normalizedDir = normalizeDir(direction);

        model.addAttribute("rows", loadRows(query, normalizedSort, normalizedDir));
        model.addAttribute("q", StringUtils.defaultString(query));
        model.addAttribute("sort", normalizedSort);
        model.addAttribute("dir", normalizedDir);
        return "results/index";
    }

    /** htmx fragment — returns only the {@code <tbody>} rows for live filter/sort swaps. */
    @GetMapping("/grid")
    public String grid(@RequestParam(name = "q", required = false) String query,
                       @RequestParam(name = "sort", defaultValue = "createdAt") String sort,
                       @RequestParam(name = "dir", defaultValue = "desc") String direction,
                       Model model) throws IOException {
        model.addAttribute("rows", loadRows(query, normalizeSort(sort), normalizeDir(direction)));
        model.addAttribute("oob", true);
        return "results/grid";
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
    @CacheEvict(value = "results", allEntries = true)
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

    //// PRIVATE ////

    private List<ResultRow> loadRows(String query, String sort, String direction) throws IOException {
        final Collection<Path> all = resultService.getAll();
        final Stream<ResultRow> rows = all.stream().map(ResultsWebController::toRow);

        final Stream<ResultRow> filtered = StringUtils.isBlank(query)
            ? rows
            : rows.filter(r -> r.uuid().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)));

        final Comparator<ResultRow> comparator = comparatorFor(sort);
        final Comparator<ResultRow> ordered = "asc".equals(direction) ? comparator : comparator.reversed();

        return filtered.sorted(ordered).collect(Collectors.toUnmodifiableList());
    }

    private static Comparator<ResultRow> comparatorFor(String sort) {
        return switch (sort) {
            case "uuid" -> Comparator.comparing(ResultRow::uuid, String.CASE_INSENSITIVE_ORDER);
            case "size" -> Comparator.comparingLong(ResultRow::sizeBytes);
            default -> Comparator.comparing(ResultRow::createdAt, String.CASE_INSENSITIVE_ORDER);
        };
    }

    private static String normalizeSort(String raw) {
        if (raw == null) return "createdAt";
        return switch (raw) {
            case "uuid", "size", "createdAt" -> raw;
            default -> "createdAt";
        };
    }

    private static String normalizeDir(String raw) {
        return "asc".equalsIgnoreCase(raw) ? "asc" : "desc";
    }

    private static ResultRow toRow(Path dir) {
        final long bytes = FileUtils.sizeOfDirectory(dir.toFile());
        String createdAt = "";
        try {
            final BasicFileAttributes attr = Files.readAttributes(dir, BasicFileAttributes.class);
            createdAt = attr.creationTime().toInstant().toString();
        } catch (IOException e) {
            log.error("Unable to read creation time for '{}'", dir, e);
        }
        return new ResultRow(dir.getFileName().toString(), bytes, createdAt);
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
