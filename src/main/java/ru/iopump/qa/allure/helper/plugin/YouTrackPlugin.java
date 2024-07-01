package ru.iopump.qa.allure.helper.plugin;

import io.qameta.allure.core.LaunchResults;
import io.qameta.allure.entity.Link;
import io.qameta.allure.entity.Status;
import io.qameta.allure.entity.TestResult;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.brewcode.api.youtrack.model.IssueCommentDto;
import ru.iopump.qa.allure.api.youtrack.IssuesClient;
import ru.iopump.qa.allure.helper.plugin.youtrack.MarkdownStatisticModel;
import ru.iopump.qa.allure.helper.plugin.youtrack.MarkdownStatisticModel.Row.Statistic;
import ru.iopump.qa.allure.helper.plugin.youtrack.MarkdownStatisticModel.Total;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.qameta.allure.entity.Status.SKIPPED;
import static io.qameta.allure.entity.Status.UNKNOWN;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.substringBefore;
import static ru.iopump.qa.allure.helper.Util.join;
import static ru.iopump.qa.allure.helper.Util.url;
import static ru.iopump.qa.allure.helper.plugin.youtrack.MarkdownStatisticModel.Row.Statistic.none;

// https://www.jetbrains.com/help/youtrack/devportal/youtrack-rest-api.html
@Slf4j
@RequiredArgsConstructor
public class YouTrackPlugin implements AllureServerPlugin {

    private static final String COMMENT_TITLE = "E2E testing scenarios";
    private static final String ALLURE_SERVER = "Allure Server";
    private final boolean dryRun;

    public YouTrackPlugin() {
        this.dryRun = false;
    }

    @Override
    public boolean isEnabled(Context context) {
        return context.tmsProperties().isEnabled();
    }

    @Override
    public void onGenerationStart(Collection<Path> resultsDirectories, Context context) {
    }

    @Override
    public void onGenerationFinish(Path reportDirectory, Collection<LaunchResults> launchResults, Context context) {
        final var effectiveDryRun = context.tmsProperties().isDryRun() || dryRun;
        final var uuid = reportDirectory.getFileName().toString();
        final var tmsHost = context.tmsProperties().getHost();
        final var issueKeyPattern = context.tmsProperties().getIssueKeyPattern();
        final var allureServerBaseUrl = url(context.getAllureProperties());
        var launchResultsGroupByIssueTags = launchResultsGroupByIssueTags(launchResults, tmsHost, issueKeyPattern);

        log.info("[PLUGIN {}] Statistic\n{}",
            getName(),
            launchResultsGroupByIssueTags.entrySet().stream()
                .map(e ->
                    "%-20s : %s".formatted(
                        e.getKey(),
                        e.getValue().stream()
                            .map(tr -> "name: %s | full-name: %s | status: %s | %s ".formatted(substringBefore(tr.getName(), '\n'), tr.getFullName(), tr.getStatus(), tr.getTime()))
                            .collect(joining("\n" + StringUtils.repeat(' ', 23)))
                    )
                )
                .collect(joining("\n"))
        );

        launchResultsGroupByIssueTags
            .entrySet()
            .parallelStream()
            .forEach(e -> {
                var issueKey = e.getKey();
                var results = e.getValue();
                try {
                    var newScenarioModel = launchResultsToMarkdownStatisticModel(results, allureServerBaseUrl, context.getReportUrl());

                    if (newScenarioModel != null) {
                        sendComment(newScenarioModel, issueKey, context, effectiveDryRun);
                        log.info("[PLUGIN {}] Comment with executed scenarios created for issue '{}' for report '{}'\n{}", getName(), issueKey, uuid, newScenarioModel.toMarkdown());
                    } else
                        log.error("[PLUGIN {}] Server Internal Error. Failed to create comment for issue '{}'. Cannot build scenario model for results. Report '{}'", getName(), issueKey, uuid);
                } catch (Throwable err) {
                    log.error("[PLUGIN %s] Failed to create comment for issue '%s'. Report '%s'".formatted(getName(), issueKey, uuid), err);
                }
            });
    }

    @Override
    public String getName() {
        return "YouTrack integration";
    }

    static void sendComment(MarkdownStatisticModel newScenarioModel, String issueKey, Context context, boolean dryRun) {
        var client = context.beanFactory().getBean(IssuesClient.class);

        List<IssueCommentDto> issueCommentDtos = !dryRun ? client.issuesIdCommentsGet(issueKey, "id,text,author", null, null) : Collections.emptyList();
        Optional<IssueCommentDto> commentWithScenarios = issueCommentDtos.stream().filter(it -> it.getText().contains(COMMENT_TITLE)).findFirst();

        final var commentToCreateOrUpdate = new IssueCommentDto();
        if (commentWithScenarios.isEmpty()) {
            commentToCreateOrUpdate.setText(newScenarioModel.toMarkdown());
            if (!dryRun)
                client.issuesIdCommentsPost(issueKey, null, true, null, commentToCreateOrUpdate);
        } else {
            var previousMarkdownText = commentWithScenarios.get().getText();
            var previousModel = MarkdownStatisticModel.toModel(previousMarkdownText);
            var mergedModel = previousModel.merge(newScenarioModel);
            commentToCreateOrUpdate.setText(mergedModel.toMarkdown());
            if (!dryRun)
                client.issuesIdCommentsIssueCommentIdPost(issueKey, commentWithScenarios.get().getId(), true, null, commentToCreateOrUpdate);
        }
    }

    @NonNull
    static Map<String, List<TestResult>> launchResultsGroupByIssueTags(Collection<LaunchResults> launchResults, String tmsHost, Pattern issuePattern) {

        return launchResults
            .stream()
            .flatMap(lr -> lr.getResults().stream()
                .flatMap(r -> r.getLinks().stream()
                    .map(link -> Map.entry(link, r))))
            .filter(e -> isIssueKey(e.getKey(), tmsHost, issuePattern))
            .map(e -> Map.entry(extractIssueKey(e.getKey(), issuePattern), e.getValue()))
            .collect(Collectors.groupingBy(
                Map.Entry::getKey,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())
            ));
    }

    @Nullable
    static MarkdownStatisticModel launchResultsToMarkdownStatisticModel(Collection<TestResult> launchResults, String baseLink, String reportLink) {

        var rows = launchResults.stream()
            .filter(scenario -> scenario.getStatus() != SKIPPED && scenario.getStatus() != UNKNOWN)
            .map(scenario ->
                new MarkdownStatisticModel.Row(
                    substringBefore(scenario.getName(), '\n'),
                    !isPassed(scenario) ? new Statistic(1, LocalDate.now().toString(), join(reportLink, "#suites", scenario.getUid())) : none,
                    isPassed(scenario) ? new Statistic(1, LocalDate.now().toString(), join(reportLink, "#suites", scenario.getUid())) : none
                )
            ).collect(
                Collectors.toMap(
                    MarkdownStatisticModel.Row::scenario,
                    it -> it,
                    MarkdownStatisticModel.Row::merge
                )
            )
            .values()
            .stream()
            .sorted(Comparator.comparing(MarkdownStatisticModel.Row::scenario))
            .toList();

        if (!rows.isEmpty())
            return new MarkdownStatisticModel(
                COMMENT_TITLE,
                rows,
                new Total(rows.size()),
                new MarkdownStatisticModel.Footer(ALLURE_SERVER, baseLink)
            );
        else
            return null;
    }

    private static boolean isPassed(TestResult result) {
        return result.getStatus() == Status.PASSED || result.getStatus() == SKIPPED;
    }

    private static boolean isIssueKey(Link allureLink, String tmsHost, Pattern issuePattern) {
        var isTms = StringUtils.substringAfter(allureLink.getUrl(), "//").startsWith(tmsHost);
        var hasKey = issuePattern.matcher(allureLink.getUrl()).find() || issuePattern.matcher(allureLink.getName()).find();
        return isTms && hasKey;
    }

    private static String extractIssueKey(Link allureLink, Pattern issuePattern) {
        Matcher matcher = issuePattern.matcher(allureLink.getName());
        if (matcher.find())
            return matcher.group(0);

        matcher = Pattern.compile("(" + issuePattern.pattern() + ")").matcher(allureLink.getUrl());
        if (matcher.find())
            return matcher.group(1);

        throw new IllegalArgumentException("Failed to extract issue key from link: " + allureLink);
    }
}
