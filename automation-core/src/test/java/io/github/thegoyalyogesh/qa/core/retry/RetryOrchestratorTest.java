package io.github.thegoyalyogesh.qa.core.retry;

import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.github.thegoyalyogesh.qa.core.retry.RunHistoryTest.result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RetryOrchestratorTest {

    private static final List<BrowserChoice> ROTATION = BrowserChoice.rotation("chrome,firefox,chrome-headless", false);

    /** Fake executor: plays back prepared results per run and records what it was asked to run. */
    private static final class ScriptedExecutor implements RunExecutor {
        final Map<Integer, List<ScenarioResult>> script;
        final List<String> calls = new ArrayList<>();

        ScriptedExecutor(Map<Integer, List<ScenarioResult>> script) {
            this.script = script;
        }

        @Override
        public List<ScenarioResult> execute(int run, BrowserChoice browser, List<ScenarioResult> onlyScenarios) {
            calls.add(run + " " + browser.label() + " "
                    + (onlyScenarios.isEmpty() ? "ALL" : onlyScenarios.stream().map(ScenarioResult::id).toList()));
            return script.getOrDefault(run, List.of());
        }
    }

    @Test
    public void retriesOnlyFailuresOnTheNextBrowserUntilMaxRuns() {
        List<ScenarioResult> run1 = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            run1.add(result("ok" + i, true));
        }
        run1.addAll(List.of(result("a", false), result("b", false), result("c", false),
                result("kd", false, "@knownDefect")));
        ScriptedExecutor executor = new ScriptedExecutor(Map.of(
                1, run1,
                2, List.of(result("a", true), result("b", false), result("c", false)),
                3, List.of(result("b", true), result("c", false))));

        RetryOrchestrator orchestrator = new RetryOrchestrator(new ThresholdPolicy(85, 3), ROTATION, "@knownDefect", executor);
        RunHistory history = orchestrator.orchestrate();

        assertThat(executor.calls).containsExactly(
                "1 chrome ALL",
                "2 firefox [a, b, c]",
                "3 chrome (headless) [b, c]");
        assertThat(orchestrator.summaries()).extracting(RunSummary::cumulativePassRate)
                .containsExactly(70.0, 80.0, 90.0);
        assertThat(history.retryCandidates()).containsExactly("c");
    }

    @Test
    public void noRetryWhenThresholdIsMetInRunOne() {
        ScriptedExecutor executor = new ScriptedExecutor(Map.of(1, List.of(
                result("a", true), result("b", true), result("kd", false, "@knownDefect"))));

        new RetryOrchestrator(new ThresholdPolicy(85, 3), ROTATION, "@knownDefect", executor).orchestrate();

        assertThat(executor.calls).containsExactly("1 chrome ALL");
    }

    @Test
    public void stopsEarlyWhenARetryReachesTheThreshold() {
        List<ScenarioResult> run1 = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            run1.add(result("ok" + i, true));
        }
        run1.addAll(List.of(result("a", false), result("b", false)));
        ScriptedExecutor executor = new ScriptedExecutor(Map.of(1, run1, 2, List.of(result("a", true), result("b", true))));

        new RetryOrchestrator(new ThresholdPolicy(85, 3), ROTATION, "@knownDefect", executor).orchestrate();

        assertThat(executor.calls).containsExactly("1 chrome ALL", "2 firefox [a, b]");
    }

    @Test
    public void runOneWithoutResultsIsABrokenBuildNotAPass() {
        ScriptedExecutor executor = new ScriptedExecutor(Map.of());
        RetryOrchestrator orchestrator = new RetryOrchestrator(new ThresholdPolicy(85, 3), ROTATION, "@knownDefect", executor);

        assertThatThrownBy(orchestrator::orchestrate).hasMessageContaining("no scenario results");
    }

    @Test
    public void forwardsDefaultAndExtraPropertiesThatAreSet() {
        Properties properties = new Properties();
        properties.setProperty("threads", "2");
        properties.setProperty("testdata.file", "/data/book.xlsx");
        properties.setProperty("retry.passThrough", "testdata.file, not.set");
        properties.setProperty("unrelated", "x");

        assertThat(RetryOrchestrator.passThroughArguments(properties))
                .containsExactly("-Dthreads=2", "-Dtestdata.file=/data/book.xlsx");
    }
}
