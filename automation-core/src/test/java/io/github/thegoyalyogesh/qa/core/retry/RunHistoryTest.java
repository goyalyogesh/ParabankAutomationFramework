package io.github.thegoyalyogesh.qa.core.retry;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RunHistoryTest {

    static ScenarioResult result(String id, boolean passed, String... tags) {
        return new ScenarioResult(id, "Feature", "Scenario " + id, Set.of(tags), passed);
    }

    @Test
    public void passRateIsCumulativeAndIgnoresKnownDefects() {
        RunHistory history = new RunHistory("@knownDefect");
        history.record(1, "chrome", List.of(result("a", true), result("b", false), result("c", false),
                result("d", true), result("kd", false, "@knownDefect")));

        assertThat(history.passRate()).isEqualTo(50.0);
        assertThat(history.retryCandidates()).containsExactly("b", "c");
        assertThat(history.knownDefects()).extracting(ScenarioResult::id).containsExactly("kd");

        history.record(2, "firefox", List.of(result("b", true), result("c", false)));

        assertThat(history.passRate()).isEqualTo(75.0);
        assertThat(history.retryCandidates()).containsExactly("c");
        assertThat(history.retryScenarios()).extracting(ScenarioResult::name).containsExactly("Scenario c");
        assertThat(history.passedOnRetry()).containsExactly("b");
        assertThat(history.attempts("b")).extracting(RunHistory.Attempt::browser).containsExactly("chrome", "firefox");
    }

    @Test
    public void onlyKnownDefectsMeansNothingToMeasure() {
        RunHistory history = new RunHistory("@knownDefect");
        history.record(1, "chrome", List.of(result("kd", false, "@knownDefect")));
        assertThat(history.passRate()).isEqualTo(100.0);
        assertThat(history.retryCandidates()).isEmpty();
    }

    @Test
    public void thresholdPolicyNeedsLowRateRunsLeftAndSomethingToRetry() {
        RunHistory history = new RunHistory("@knownDefect");
        history.record(1, "chrome", List.of(result("a", true), result("b", false)));
        ThresholdPolicy policy = new ThresholdPolicy(85, 3);

        assertThat(policy.anotherRun(history, 1)).isTrue();
        assertThat(policy.anotherRun(history, 3)).as("no runs left").isFalse();
        assertThat(policy.met(history)).isFalse();
        assertThat(new ThresholdPolicy(50, 3).anotherRun(history, 1)).as("50% is enough").isFalse();
        assertThatThrownBy(() -> new ThresholdPolicy(120, 3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void browserRotationParsesHeadlessSuffix() {
        assertThat(BrowserChoice.rotation("chrome, firefox ,chrome-headless", false)).containsExactly(
                new BrowserChoice("chrome", false), new BrowserChoice("firefox", false), new BrowserChoice("chrome", true));
        assertThat(BrowserChoice.rotation("firefox", true)).containsExactly(new BrowserChoice("firefox", true));
        assertThat(new BrowserChoice("chrome", true).label()).isEqualTo("chrome (headless)");
        assertThatThrownBy(() -> BrowserChoice.rotation(" , ", false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void browserRotationParsesOptionalEnginePrefix() {
        assertThat(BrowserChoice.rotation("chrome,playwright:chrome-headless, PLAYWRIGHT:firefox", false)).containsExactly(
                new BrowserChoice("selenium", "chrome", false),
                new BrowserChoice("playwright", "chrome", true),
                new BrowserChoice("playwright", "firefox", false));
        assertThat(new BrowserChoice("chrome", false).engine()).isEqualTo("selenium");
        assertThat(new BrowserChoice("chrome", false).label()).isEqualTo("chrome");
        assertThat(new BrowserChoice("playwright", "chrome", true).label()).isEqualTo("playwright chrome (headless)");
        assertThatThrownBy(() -> BrowserChoice.rotation(":chrome", false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void knownDefectThatPassesIsReportedAsPossiblyFixed() {
        RunHistory history = new RunHistory("@knownDefect");
        history.record(1, "chrome", List.of(result("a", true),
                result("kd-still-broken", false, "@knownDefect"), result("kd-fixed", true, "@knownDefect")));

        assertThat(history.knownDefectsThatPassed()).extracting(ScenarioResult::id).containsExactly("kd-fixed");
        assertThat(history.passRate()).as("still not counted").isEqualTo(100.0);
    }
}
