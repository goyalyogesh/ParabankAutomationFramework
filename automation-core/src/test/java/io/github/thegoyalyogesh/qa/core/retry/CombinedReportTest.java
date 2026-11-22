package io.github.thegoyalyogesh.qa.core.retry;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class CombinedReportTest {

    @Test
    public void showsRunsScenarioHistoryAndKnownDefects() throws IOException {
        RunHistory history = new RunHistory("@knownDefect");
        history.record(1, "chrome", List.of(
                new ScenarioResult("f:1", "Login", "U01 <logs> in", Set.of(), true),
                new ScenarioResult("f:2", "Login", "U02 logs in", Set.of(), false),
                new ScenarioResult("f:3", "Login", "N01 rejected", Set.of("@knownDefect"), false)));
        history.record(2, "firefox", List.of(new ScenarioResult("f:2", "Login", "U02 logs in", Set.of(), true)));
        List<RunSummary> runs = List.of(
                new RunSummary(1, new BrowserChoice("chrome", false), 3, 1, 2, 50.0),
                new RunSummary(2, new BrowserChoice("firefox", false), 1, 1, 0, 100.0));

        Path dir = Files.createTempDirectory("combined");
        Path report = CombinedReport.write(dir, history, runs, new ThresholdPolicy(85, 3));

        assertThat(report).isEqualTo(dir.resolve("combined").resolve("index.html"));
        assertThat(Files.readString(report))
                .contains("PASSED", "100.0%", "85.0%")
                .contains("../run-1/reports/spark.html", "../run-2/cucumber/")
                .contains("U01 &lt;logs&gt; in")
                .contains("Passed on retry")
                .contains("Known defect")
                .contains("N01 rejected");
    }
}
