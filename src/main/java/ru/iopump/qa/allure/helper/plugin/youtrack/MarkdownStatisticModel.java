package ru.iopump.qa.allure.helper.plugin.youtrack;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public record MarkdownStatisticModel(
    String title,
    List<Row> scenarioStatisticRows,
    Total total,
    Footer footer
) {

    public String toMarkdown() {
        return """
            ### %s

            | **Scenario** | ❌ `Failed` | ✅ `Passed` |
            |--------------|-------------|--------------|
            %s

            %s

            %s
            """.formatted(
            title,
            scenarioStatisticRows.stream().sorted(comparing(Row::scenario)).map(Row::toMarkdown).collect(joining("\n")),
            total.toMarkdown(),
            footer.toMarkdown());
    }

    public static MarkdownStatisticModel toModel(String text) {
        var lines = text.lines()
            .filter(it -> !it.isBlank())
            .toList();
        var linesIterator = lines.iterator();

        var title = linesIterator.hasNext() ? StringUtils.substringAfter(linesIterator.next(), "###").trim() : "parsing_error";
        if (linesIterator.hasNext()) linesIterator.next();
        if (linesIterator.hasNext()) linesIterator.next();
        var rows = new ArrayList<Row>();
        String rowText = null;
        while (linesIterator.hasNext()) {
            rowText = linesIterator.next();
            var newRow = Row.toModel(rowText);
            if (newRow == Row.error)
                break;
            rows.add(newRow);
            rowText = null;
        }
        var total = rowText != null ? Total.toModel(rowText) : Total.error;
        var footer = linesIterator.hasNext() ? Footer.toModel(linesIterator.next()) : Footer.error;

        var model = new MarkdownStatisticModel(title, rows, total, footer);

        return model.hasError()
            ? new MarkdownStatisticModel(title, rows, total, new Footer(footer.generatedBy + " **`with errors check logs`**", footer.link))
            : model;
    }

    public MarkdownStatisticModel merge(MarkdownStatisticModel other) {
        var rows = this.mergeRows(other.scenarioStatisticRows);
        return new MarkdownStatisticModel(
            this.title,
            this.mergeRows(other.scenarioStatisticRows),
            new Total(rows.size()),
            this.footer.merge(other.footer)
        );
    }

    private List<Row> mergeRows(List<Row> other) {
        return Stream.concat(this.scenarioStatisticRows.stream(), other.stream())
            .collect(toMap(
                it -> it.scenario,
                it -> it,
                Row::merge,
                LinkedHashMap::new))
            .values()
            .stream()
            .sorted(comparing(Row::scenario))
            .toList();
    }

    public boolean hasError() {
        return scenarioStatisticRows.stream().anyMatch(Row::hasError) || total.hasError() || footer.hasError();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public record Row(
        String scenario,
        Statistic passed,
        Statistic failed
    ) {
        private static final Row error = new Row("parsing_error", Statistic.error, Statistic.error);
        private static final Pattern pattern = Pattern.compile("\\| (.+?) \\| (.+?) \\| (.+?) \\|");
        private static final String md_template = "| %s | %s | %s |";

        public String toMarkdown() {
            return String.format(md_template, scenario, passed.toMarkdown(), failed.toMarkdown());
        }

        public static Row toModel(String text) {
            var matcher = pattern.matcher(text);

            if (matcher.find())
                return new Row(
                    matcher.group(1).trim(),
                    Statistic.toModel(matcher.group(2).trim()),
                    Statistic.toModel(matcher.group(3).trim())
                );
            else
                return error;
        }

        public Row merge(Row other) {
            if (other == error) return this;
            return this == error ? other : new Row(
                this.scenario,
                this.passed.merge(other.passed),
                this.failed.merge(other.failed)
            );
        }

        public boolean hasError() {
            return this == error || passed.hasError() || failed.hasError();
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

        public record Statistic(
            int count,
            String latestDate,
            String latestLink
        ) {
            public static final Statistic none = new Statistic(0, "", "");
            static final Statistic error = new Statistic(0, "parsing_error", "parsing_error");
            private static final Pattern pattern = Pattern.compile("\\*\\*(\\d+)\\*\\* times \\[`latest` on (.+)]\\((.+)\\)");
            private static final String md_template = "**%d** times [`latest` on %s](%s)";

            public String toMarkdown() {
                return String.format(md_template, count, latestDate, latestLink);
            }

            public static Statistic toModel(String text) {
                var matcher = pattern.matcher(text);

                if (matcher.find())
                    return new Statistic(
                        Integer.parseInt(matcher.group(1).trim()),
                        matcher.group(2).trim(),
                        matcher.group(3).trim()
                    );
                else
                    return error;
            }

            public Statistic merge(Statistic other) {
                if (other == error || other.count <= 0) return this;
                return this == error ? other : new Statistic(
                    this.count + other.count,
                    isNotBlank(other.latestDate) ? other.latestDate : this.latestDate,
                    isNotBlank(other.latestLink) ? other.latestLink : this.latestLink
                );
            }

            public boolean hasError() {
                return this == error;
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public record Total(int total) {
        private static final Total error = new Total(-1);
        private static final Pattern pattern = Pattern.compile("Total test scenarios: \\*\\*(\\d+)\\*\\*");
        private static final String md_template = "Total test scenarios: **%d**";

        public String toMarkdown() {
            return String.format(md_template, total);
        }

        public static Total toModel(String text) {
            var matcher = pattern.matcher(text);

            if (matcher.find())
                return new Total(Integer.parseInt(matcher.group(1).trim()));
            else
                return error;
        }

        public boolean hasError() {
            return this == error;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public record Footer(String generatedBy, String link) {
        private static final Footer error = new Footer("parsing_error", "parsing_error");
        private static final Pattern pattern = Pattern.compile("_Generated by \\[(.+)]\\((.+)\\)_");
        private static final String md_template = "_Generated by [%s](%s)_";

        public String toMarkdown() {
            return String.format(md_template, generatedBy, link);
        }

        public static Footer toModel(String text) {
            var matcher = pattern.matcher(text);

            if (matcher.find())
                return new Footer(
                    matcher.group(1),
                    matcher.group(2)
                );
            else
                return error;
        }

        public Footer merge(Footer other) {
            return new Footer(
                other.generatedBy,
                other.link
            );
        }

        public boolean hasError() {
            return this == error;
        }
    }
}
