package io.github.thegoyalyogesh.qa.core.retry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Single self-contained HTML page: verdict, one row per run, and every scenario's result in every run. */
public final class CombinedReport {

    private CombinedReport() {
    }

    public static Path write(Path reportsDir, RunHistory history, List<RunSummary> runs, ThresholdPolicy policy) {
        Path file = reportsDir.resolve("combined").resolve("index.html");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, html(history, runs, policy));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
        return file;
    }

    static String html(RunHistory history, List<RunSummary> runs, ThresholdPolicy policy) {
        boolean met = policy.met(history);
        StringBuilder html = new StringBuilder("""
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Combined test report</title><style>
                body{font:14px/1.5 system-ui,sans-serif;margin:0 auto;max-width:1100px;padding:24px 16px;color:#1d2433;background:#fff}
                h1{margin:0 0 4px}h2{margin-top:32px}.muted{color:#5b6478}
                .verdict{display:inline-block;padding:6px 14px;border-radius:6px;font-weight:700;color:#fff}
                .ok{background:#1f7a4d}.bad{background:#b3261e}
                .scroll{overflow-x:auto}table{border-collapse:collapse;width:100%}
                th,td{border-bottom:1px solid #e3e6ee;padding:6px 8px;text-align:left;vertical-align:top}
                th{background:#f4f6fa}.p{color:#1f7a4d;font-weight:600}.f{color:#b3261e;font-weight:600}
                .k{color:#8a5a00;font-weight:600}.n{color:#9aa1b2}
                </style></head><body>
                """);
        html.append("<h1>Combined test report</h1><p class=\"muted\">Generated ")
                .append(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)).append("</p>");
        html.append("<p><span class=\"verdict ").append(met ? "ok\">PASSED" : "bad\">FAILED").append("</span> ")
                .append("Cumulative pass rate <b>").append(percent(history.passRate())).append("</b>, threshold ")
                .append(percent(policy.thresholdPercent())).append(" (known defects excluded). ")
                .append(history.knownDefects().size()).append(" known defect(s), ")
                .append(history.passedOnRetry().size()).append(" passed on retry, ")
                .append(history.retryCandidates().size()).append(" still failing.</p>");

        html.append("<h2>Runs</h2><div class=\"scroll\"><table><tr><th>Run</th><th>Browser</th><th>Executed</th>")
                .append("<th>Passed</th><th>Failed</th><th>Cumulative pass rate</th><th>Reports</th></tr>");
        for (RunSummary run : runs) {
            String dir = "../run-" + run.run() + "/";
            html.append("<tr><td>").append(run.run()).append("</td><td>").append(escape(run.browser().label()))
                    .append("</td><td>").append(run.executed()).append("</td><td>").append(run.passed())
                    .append("</td><td>").append(run.failed()).append("</td><td>").append(percent(run.cumulativePassRate()))
                    .append("</td><td><a href=\"").append(dir).append("reports/spark.html\">Extent</a> · ")
                    .append("<a href=\"").append(dir).append("cucumber/\">Cucumber</a> · ")
                    .append("<a href=\"").append(dir).append("maven.log\">log</a></td></tr>");
        }
        html.append("</table></div>");

        html.append("<h2>Scenarios</h2>");
        Map<String, List<ScenarioResult>> byFeature = history.scenarios().stream()
                .collect(Collectors.groupingBy(ScenarioResult::feature, LinkedHashMap::new, Collectors.toList()));
        byFeature.forEach((feature, scenarios) -> {
            html.append("<h3>").append(escape(feature)).append("</h3><div class=\"scroll\"><table><tr><th>Scenario</th>");
            runs.forEach(run -> html.append("<th>Run ").append(run.run()).append("</th>"));
            html.append("<th>Result</th></tr>");
            for (ScenarioResult scenario : scenarios) {
                Map<Integer, Boolean> byRun = history.attempts(scenario.id()).stream()
                        .collect(Collectors.toMap(RunHistory.Attempt::run, RunHistory.Attempt::passed));
                html.append("<tr><td>").append(escape(scenario.name())).append("</td>");
                for (RunSummary run : runs) {
                    Boolean passed = byRun.get(run.run());
                    html.append(passed == null ? "<td class=\"n\">–</td>"
                            : passed ? "<td class=\"p\">passed</td>" : "<td class=\"f\">failed</td>");
                }
                html.append("<td>").append(verdict(history, scenario.id())).append("</td></tr>");
            }
            html.append("</table></div>");
        });

        html.append("<h2>Known defects</h2><p class=\"muted\">Fail on purpose: the application has a bug. ")
                .append("Not counted in the pass rate and never retried.</p><ul>");
        history.knownDefects().forEach(s -> html.append("<li>").append(escape(s.name())).append(" <span class=\"muted\">")
                .append(escape(s.id())).append("</span></li>"));
        html.append("</ul></body></html>\n");
        return html.toString();
    }

    private static String verdict(RunHistory history, String id) {
        if (history.isKnownDefect(id)) {
            return "<span class=\"k\">Known defect</span>";
        }
        if (history.passedOnRetry().contains(id)) {
            return "<span class=\"p\">Passed on retry</span>";
        }
        return history.passedInAnyRun(id) ? "<span class=\"p\">Passed</span>" : "<span class=\"f\">Failed</span>";
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
