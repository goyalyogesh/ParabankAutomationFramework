# Plan 04 — Retry Engine & Combined Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One command runs the whole suite and, while the cumulative pass rate (known defects excluded) is below 85%,
re-runs only the failed scenarios on the next browser of a rotation (default Chrome → Firefox → headless Chrome), at
most 3 runs. Every run keeps its own Extent + Cucumber reports in `reports/run-N/`, and a combined HTML report in
`reports/combined/` shows each scenario's history across runs.

**Architecture:** Generic, reusable logic in `automation-core` package `core.retry`:
`CucumberJsonReader` (JSON → `ScenarioResult`), `RunHistory` (attempts per scenario, cumulative pass rate),
`ThresholdPolicy`, `BrowserChoice` (rotation), `RetryOrchestrator` (the loop, given a `RunExecutor`),
`MavenRunExecutor` (one `mvn test` process per run, archives outputs), `CombinedReport` (HTML).
`parabank-tests` only wires it up: exec-plugin configuration, a fault-injection hook to demonstrate the retry path,
and a run-title property.

**Why a separate Maven process per run:** each run starts clean (static state, ThreadLocals, Extent singletons,
Surefire settings) exactly as Jenkins would run it, and the orchestrator only reads the JSON the run leaves behind.

**How a re-run selects scenarios:** `-Dcucumber.features=classpath:features/login.feature:21,classpath:...:35`.
Each runner still applies its own tags, so every scenario re-runs in the right runner and block.

**Tech Stack:** Gson (already managed, 2.13.1) added to core; exec-maven-plugin 3.5.0.

**Spec items:** E3 (retry engine, threshold, rotation, cumulative %), E5 (per-run + combined reports).

---

## File map

| File | Responsibility |
|---|---|
| `automation-core/pom.xml` | + gson |
| `CORE/main/.../core/retry/ScenarioResult.java` | one scenario's outcome in one run |
| `CORE/main/.../core/retry/CucumberJsonReader.java` | Cucumber JSON report(s) → results |
| `CORE/main/.../core/retry/RunHistory.java` | attempts per scenario, cumulative pass rate, retry candidates |
| `CORE/main/.../core/retry/ThresholdPolicy.java` | "another run?" decision |
| `CORE/main/.../core/retry/BrowserChoice.java` | `chrome`, `firefox-headless`, rotation parsing |
| `CORE/main/.../core/retry/RunExecutor.java`, `RunSummary.java` | executor contract, per-run numbers |
| `CORE/main/.../core/retry/RetryOrchestrator.java` | the loop + `main` |
| `CORE/main/.../core/retry/MavenRunExecutor.java` | runs `mvn test` per run, archives to `reports/run-N` |
| `CORE/main/.../core/retry/CombinedReport.java` | `reports/combined/index.html` |
| `pom.xml` | exec-maven-plugin version in pluginManagement |
| `parabank-tests/pom.xml` | exec plugin configured for the orchestrator; `report.title` property |
| `PB/test/.../hooks/Hooks.java` | `-Dinject.failure=<regex>` fault injection |

---

### Task 1: Reading Cucumber's JSON report

- [ ] **Step 1: `automation-core/pom.xml`** — add after the `log4j-api` dependency:

```xml
        <!-- JSON parsing for the retry engine (reads Cucumber's JSON report) -->
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
        </dependency>
```

- [ ] **Step 2: Failing test `CORE/test/.../core/retry/CucumberJsonReaderTest.java`**

```java
package io.github.thegoyalyogesh.qa.core.retry;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CucumberJsonReaderTest {

    private static final String LOGIN_JSON = """
            [{"uri": "classpath:features/login.feature", "name": "Login", "elements": [
              {"type": "background", "line": 4, "name": "", "steps": [{"result": {"status": "passed"}}]},
              {"type": "scenario", "line": 21, "name": "U01 logs in", "tags": [{"name": "@login"}],
               "before": [{"result": {"status": "passed"}}],
               "steps": [{"result": {"status": "passed"}}, {"result": {"status": "passed"}}],
               "after": [{"result": {"status": "passed"}}]},
              {"type": "scenario", "line": 35, "name": "N01 rejected", "tags": [{"name": "@login"}, {"name": "@knownDefect"}],
               "steps": [{"result": {"status": "passed"}}, {"result": {"status": "failed"}}, {"result": {"status": "skipped"}}]},
              {"type": "scenario", "line": 40, "name": "Injected", "tags": [],
               "before": [{"result": {"status": "failed"}}],
               "steps": [{"result": {"status": "skipped"}}]}
            ]}]
            """;

    @Test
    public void readsScenariosWithIdsTagsAndStatus() throws IOException {
        Path dir = Files.createTempDirectory("cucumber");
        Files.writeString(dir.resolve("login.json"), LOGIN_JSON);
        Files.writeString(dir.resolve("empty.json"), "");
        Files.writeString(dir.resolve("login.html"), "<html>not json</html>");

        List<ScenarioResult> results = CucumberJsonReader.readDirectory(dir);

        assertThat(results).extracting(ScenarioResult::id).containsExactly(
                "classpath:features/login.feature:21",
                "classpath:features/login.feature:35",
                "classpath:features/login.feature:40");
        assertThat(results).extracting(ScenarioResult::passed).containsExactly(true, false, false);
        assertThat(results.get(1).tags()).containsExactly("@login", "@knownDefect");
        assertThat(results.get(0).feature()).isEqualTo("Login");
        assertThat(results.get(0).name()).isEqualTo("U01 logs in");
    }

    @Test
    public void missingDirectoryMeansNoResults() {
        assertThat(CucumberJsonReader.readDirectory(Path.of("does-not-exist-" + System.nanoTime()))).isEmpty();
    }
}
```

- [ ] **Step 3:** `mvn -B -pl automation-core test -Dtest=CucumberJsonReaderTest` → `cannot find symbol: class ScenarioResult`.

- [ ] **Step 4: `ScenarioResult.java`**

```java
package io.github.thegoyalyogesh.qa.core.retry;

import java.util.Set;

/**
 * One scenario's outcome in one run. {@code id} is {@code uri:line} (e.g. {@code classpath:features/login.feature:21}),
 * the format Cucumber accepts to run exactly that scenario again.
 */
public record ScenarioResult(String id, String feature, String name, Set<String> tags, boolean passed) {

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}
```

- [ ] **Step 5: `CucumberJsonReader.java`**

```java
package io.github.thegoyalyogesh.qa.core.retry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Reads Cucumber's {@code json:} plugin output. A scenario passed only if every hook and step passed. */
public final class CucumberJsonReader {

    private CucumberJsonReader() {
    }

    /** All scenarios from every {@code *.json} file in the directory (one file per runner); none if it doesn't exist. */
    public static List<ScenarioResult> readDirectory(Path directory) {
        if (Files.notExists(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .flatMap(file -> read(file).stream())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + directory, e);
        }
    }

    public static List<ScenarioResult> read(Path file) {
        String json;
        try {
            json = Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
        if (json.isBlank()) {
            return List.of(); // a runner that ran nothing can leave an empty file
        }
        List<ScenarioResult> results = new ArrayList<>();
        for (JsonElement featureElement : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject feature = featureElement.getAsJsonObject();
            String uri = feature.get("uri").getAsString();
            String featureName = feature.get("name").getAsString();
            for (JsonElement element : array(feature, "elements")) {
                JsonObject scenario = element.getAsJsonObject();
                if (!"scenario".equals(scenario.get("type").getAsString())) {
                    continue; // backgrounds are reported separately; their steps are part of each scenario run
                }
                Set<String> tags = stream(array(scenario, "tags"))
                        .map(tag -> tag.getAsJsonObject().get("name").getAsString())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                boolean passed = Stream.of("before", "steps", "after")
                        .flatMap(section -> stream(array(scenario, section)))
                        .map(item -> item.getAsJsonObject().getAsJsonObject("result").get("status").getAsString())
                        .allMatch("passed"::equals);
                results.add(new ScenarioResult(uri + ":" + scenario.get("line").getAsInt(), featureName,
                        scenario.get("name").getAsString(), tags, passed));
            }
        }
        return results;
    }

    private static JsonArray array(JsonObject object, String member) {
        return object.has(member) ? object.getAsJsonArray(member) : new JsonArray();
    }

    private static Stream<JsonElement> stream(JsonArray array) {
        return StreamSupport.stream(array.spliterator(), false);
    }
}
```

- [ ] **Step 6:** Run → 2/2 pass. Commit `feat(core): read Cucumber JSON results for the retry engine`.

---

### Task 2: Run history, threshold policy, browser rotation

- [ ] **Step 1: Failing test `CORE/test/.../core/retry/RunHistoryTest.java`**

```java
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
}
```

- [ ] **Step 2:** Run → `cannot find symbol: class RunHistory`.

- [ ] **Step 3: `RunHistory.java`**

```java
package io.github.thegoyalyogesh.qa.core.retry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every attempt of every scenario across runs. The pass rate is cumulative: a scenario counts as passed if it passed
 * in any run so far. Scenarios tagged with the known-defect tag are reported but never counted or retried.
 */
public final class RunHistory {

    /** One execution of one scenario. */
    public record Attempt(int run, String browser, boolean passed) {
    }

    private final String knownDefectTag;
    /** First result seen per scenario (name, feature, tags); insertion order = order of run 1. */
    private final Map<String, ScenarioResult> scenarios = new LinkedHashMap<>();
    private final Map<String, List<Attempt>> attempts = new LinkedHashMap<>();

    public RunHistory(String knownDefectTag) {
        this.knownDefectTag = knownDefectTag;
    }

    public void record(int run, String browser, Collection<ScenarioResult> results) {
        for (ScenarioResult result : results) {
            scenarios.putIfAbsent(result.id(), result);
            attempts.computeIfAbsent(result.id(), id -> new ArrayList<>())
                    .add(new Attempt(run, browser, result.passed()));
        }
    }

    public List<ScenarioResult> scenarios() {
        return List.copyOf(scenarios.values());
    }

    public List<Attempt> attempts(String scenarioId) {
        return List.copyOf(attempts.getOrDefault(scenarioId, List.of()));
    }

    public boolean isKnownDefect(String scenarioId) {
        return scenarios.get(scenarioId).hasTag(knownDefectTag);
    }

    public boolean passedInAnyRun(String scenarioId) {
        return attempts.getOrDefault(scenarioId, List.of()).stream().anyMatch(Attempt::passed);
    }

    /** Percentage of counted scenarios (not known defects) that passed in any run; 100 if nothing is counted. */
    public double passRate() {
        List<String> counted = scenarios.keySet().stream().filter(id -> !isKnownDefect(id)).toList();
        if (counted.isEmpty()) {
            return 100.0;
        }
        long passed = counted.stream().filter(this::passedInAnyRun).count();
        return 100.0 * passed / counted.size();
    }

    /** Scenarios to run again: not a known defect and not passed in any run yet (in run-1 order). */
    public List<String> retryCandidates() {
        return scenarios.keySet().stream()
                .filter(id -> !isKnownDefect(id))
                .filter(id -> !passedInAnyRun(id))
                .toList();
    }

    public List<ScenarioResult> knownDefects() {
        return scenarios.values().stream().filter(s -> s.hasTag(knownDefectTag)).toList();
    }

    /** Failed in their first run and passed later: flaky or environment-dependent, worth investigating. */
    public List<String> passedOnRetry() {
        return attempts.entrySet().stream()
                .filter(e -> !e.getValue().get(0).passed() && e.getValue().stream().anyMatch(Attempt::passed))
                .map(Map.Entry::getKey)
                .toList();
    }
}
```

- [ ] **Step 4: `ThresholdPolicy.java`**

```java
package io.github.thegoyalyogesh.qa.core.retry;

/** Decides whether another run is worth it: below the threshold, runs left, and something to retry. */
public record ThresholdPolicy(double thresholdPercent, int maxRuns) {

    public ThresholdPolicy {
        if (thresholdPercent < 0 || thresholdPercent > 100) {
            throw new IllegalArgumentException("Threshold must be 0-100, was " + thresholdPercent);
        }
        if (maxRuns < 1) {
            throw new IllegalArgumentException("maxRuns must be at least 1, was " + maxRuns);
        }
    }

    public boolean anotherRun(RunHistory history, int runsDone) {
        return runsDone < maxRuns && !met(history) && !history.retryCandidates().isEmpty();
    }

    public boolean met(RunHistory history) {
        return history.passRate() >= thresholdPercent;
    }
}
```

- [ ] **Step 5: `BrowserChoice.java`**

```java
package io.github.thegoyalyogesh.qa.core.retry;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** One browser of the rotation: {@code chrome}, {@code firefox}, or with a {@code -headless} suffix. */
public record BrowserChoice(String browser, boolean headless) {

    private static final String HEADLESS_SUFFIX = "-headless";

    /** e.g. {@code "chrome,firefox,chrome-headless"}; entries without the suffix use {@code defaultHeadless}. */
    public static List<BrowserChoice> rotation(String commaSeparated, boolean defaultHeadless) {
        List<BrowserChoice> rotation = Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .map(entry -> parse(entry, defaultHeadless))
                .toList();
        if (rotation.isEmpty()) {
            throw new IllegalArgumentException("Browser rotation is empty: '" + commaSeparated + "'");
        }
        return rotation;
    }

    static BrowserChoice parse(String entry, boolean defaultHeadless) {
        String value = entry.toLowerCase(Locale.ROOT);
        return value.endsWith(HEADLESS_SUFFIX)
                ? new BrowserChoice(value.substring(0, value.length() - HEADLESS_SUFFIX.length()), true)
                : new BrowserChoice(value, defaultHeadless);
    }

    public String label() {
        return browser + (headless ? " (headless)" : "");
    }
}
```

- [ ] **Step 6:** Run → 4/4 pass. Commit `feat(core): run history with cumulative pass rate, threshold policy, browser rotation`.

---

### Task 3: The orchestrator loop

- [ ] **Step 1: Failing test `CORE/test/.../core/retry/RetryOrchestratorTest.java`**

```java
package io.github.thegoyalyogesh.qa.core.retry;

import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        public List<ScenarioResult> execute(int run, BrowserChoice browser, List<String> onlyScenarios) {
            calls.add(run + " " + browser.label() + " " + (onlyScenarios.isEmpty() ? "ALL" : onlyScenarios));
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
}
```

- [ ] **Step 2:** Run → `cannot find symbol: class RunExecutor`.

- [ ] **Step 3: `RunExecutor.java`, `RunSummary.java`**

```java
package io.github.thegoyalyogesh.qa.core.retry;

import java.util.List;

/** Runs the suite once. {@code onlyScenarios} empty = everything; otherwise only these {@code uri:line} ids. */
@FunctionalInterface
public interface RunExecutor {

    List<ScenarioResult> execute(int run, BrowserChoice browser, List<String> onlyScenarios);
}
```

```java
package io.github.thegoyalyogesh.qa.core.retry;

/** Numbers of one run, plus the cumulative pass rate after it. */
public record RunSummary(int run, BrowserChoice browser, int executed, int passed, int failed,
                         double cumulativePassRate) {
}
```

- [ ] **Step 4: `RetryOrchestrator.java`**

```java
package io.github.thegoyalyogesh.qa.core.retry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Run 1 executes every scenario. While the cumulative pass rate (known defects excluded) is below the threshold and
 * runs are left, the next run executes only the scenarios that haven't passed yet, on the next browser of the rotation.
 * <p>
 * Command line (from the repository root, after {@code mvn install -DskipTests}):
 * <pre>
 * mvn -pl parabank-tests exec:java [-Dthreshold=85] [-Dretry.maxRuns=3]
 *     [-Dbrowser.rotation=chrome,firefox,chrome-headless] [-Dheadless=true] [-Dthreads=3]
 *     [-Dretry.demo.injectFailure=&lt;regex of scenario names to fail in run 1&gt;]
 * </pre>
 */
public final class RetryOrchestrator {

    public static final String KNOWN_DEFECT_TAG = "@knownDefect";

    /** System properties forwarded to every run, if set. */
    private static final List<String> PASS_THROUGH = List.of("threads", "timeout.seconds", "app.url", "api.url", "suiteXmlFile");

    private final ThresholdPolicy policy;
    private final List<BrowserChoice> rotation;
    private final RunHistory history;
    private final RunExecutor executor;
    private final List<RunSummary> summaries = new ArrayList<>();

    public RetryOrchestrator(ThresholdPolicy policy, List<BrowserChoice> rotation, String knownDefectTag,
                             RunExecutor executor) {
        this.policy = policy;
        this.rotation = List.copyOf(rotation);
        this.history = new RunHistory(knownDefectTag);
        this.executor = executor;
    }

    public RunHistory orchestrate() {
        List<String> onlyScenarios = List.of();
        for (int run = 1; ; run++) {
            BrowserChoice browser = rotation.get((run - 1) % rotation.size());
            say("Run %d on %s: %s", run, browser.label(),
                    onlyScenarios.isEmpty() ? "all scenarios" : onlyScenarios.size() + " scenario(s) to retry");

            List<ScenarioResult> results = executor.execute(run, browser, onlyScenarios);
            if (run == 1 && results.isEmpty()) {
                throw new IllegalStateException("Run 1 produced no scenario results - the build or test run is broken. "
                        + "Check the Maven log of run 1.");
            }
            history.record(run, browser.label(), results);
            int passed = (int) results.stream().filter(ScenarioResult::passed).count();
            summaries.add(new RunSummary(run, browser, results.size(), passed, results.size() - passed, history.passRate()));
            say("Run %d done: %d executed, %d passed, %d failed. Cumulative pass rate %.1f%% (threshold %.1f%%, known defects excluded)",
                    run, results.size(), passed, results.size() - passed, history.passRate(), policy.thresholdPercent());

            if (!policy.anotherRun(history, run)) {
                return history;
            }
            onlyScenarios = history.retryCandidates();
        }
    }

    public List<RunSummary> summaries() {
        return List.copyOf(summaries);
    }

    public RunHistory history() {
        return history;
    }

    static void say(String format, Object... args) {
        System.out.println("[retry] " + String.format(Locale.ROOT, format, args));
    }

    public static void main(String[] args) {
        ThresholdPolicy policy = new ThresholdPolicy(
                Double.parseDouble(System.getProperty("threshold", "85")), Integer.getInteger("retry.maxRuns", 3));
        List<BrowserChoice> rotation = BrowserChoice.rotation(
                System.getProperty("browser.rotation", "chrome,firefox,chrome-headless"), Boolean.getBoolean("headless"));
        Path moduleDir = Path.of(System.getProperty("retry.moduleDir", ".")).toAbsolutePath().normalize();
        Path reportsDir = Path.of(System.getProperty("retry.reportsDir", moduleDir.resolve("reports").toString()))
                .toAbsolutePath().normalize();
        List<String> passThrough = PASS_THROUGH.stream()
                .filter(key -> System.getProperty(key) != null)
                .map(key -> "-D" + key + "=" + System.getProperty(key))
                .toList();
        String injectFailure = System.getProperty("retry.demo.injectFailure");

        MavenRunExecutor executor = new MavenRunExecutor(moduleDir, reportsDir,
                System.getProperty("maven.cmd", "mvn"), passThrough,
                run -> run == 1 && injectFailure != null
                        ? List.of("-Dinject.failure=" + injectFailure)
                        : List.of());
        executor.cleanReports();
        RetryOrchestrator orchestrator = new RetryOrchestrator(policy, rotation, KNOWN_DEFECT_TAG, executor);

        RunHistory history = orchestrator.orchestrate();
        Path report = CombinedReport.write(reportsDir, history, orchestrator.summaries(), policy);

        say("Combined report: %s", report);
        Stream.of(history.passedOnRetry().isEmpty() ? null : "Passed only on retry: " + history.passedOnRetry(),
                        history.retryCandidates().isEmpty() ? null : "Still failing: " + history.retryCandidates(),
                        "Known defects (not counted): " + history.knownDefects().size())
                .filter(line -> line != null)
                .forEach(line -> say("%s", line));
        if (!policy.met(history)) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                    "Pass rate %.1f%% is below the threshold of %.1f%% after %d run(s). See %s",
                    history.passRate(), policy.thresholdPercent(), orchestrator.summaries().size(), report));
        }
        say("PASSED: %.1f%% >= %.1f%%", history.passRate(), policy.thresholdPercent());
    }
}
```

- [ ] **Step 5:** `MavenRunExecutor` and `CombinedReport` are referenced by `main`; create them in Task 4 before compiling.
Run the orchestrator test after Task 4 → 4/4 pass.

---

### Task 4: Maven executor and combined report

- [ ] **Step 1: Failing tests `MavenRunExecutorTest.java`, `CombinedReportTest.java`**

```java
package io.github.thegoyalyogesh.qa.core.retry;

import org.testng.annotations.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MavenRunExecutorTest {

    @Test
    public void commandSelectsBrowserTitleScenariosAndRunSpecificArguments() {
        MavenRunExecutor executor = new MavenRunExecutor(Path.of("module"), Path.of("reports"), "mvn",
                List.of("-Dthreads=2"), run -> run == 1 ? List.of("-Dinject.failure=U01") : List.of());

        assertThat(executor.command(1, new BrowserChoice("chrome", false), List.of())).containsExactly(
                "mvn", "-B", "test", "-Dbrowser=chrome", "-Dheadless=false", "-Dreport.title=Run 1 - chrome",
                "-Dthreads=2", "-Dinject.failure=U01");

        assertThat(executor.command(2, new BrowserChoice("firefox", true), List.of("f.feature:3", "g.feature:9")))
                .containsExactly("mvn", "-B", "test", "-Dbrowser=firefox", "-Dheadless=true",
                        "-Dreport.title=Run 2 - firefox (headless)", "-Dcucumber.features=f.feature:3,g.feature:9",
                        "-Dthreads=2");
    }
}
```

```java
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
```

- [ ] **Step 2: `MavenRunExecutor.java`**

```java
package io.github.thegoyalyogesh.qa.core.retry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Stream;

/**
 * Runs {@code mvn test} for a module in a separate process per run, then archives the run's outputs
 * ({@code target/cucumber, reports, screenshots, rerun}) to {@code reportsDir/run-N} and reads the results from there.
 * A fresh JVM per run means no state leaks between runs, exactly like separate Jenkins builds.
 */
public final class MavenRunExecutor implements RunExecutor {

    static final List<String> OUTPUT_DIRS = List.of("cucumber", "reports", "screenshots", "rerun");

    private final Path moduleDir;
    private final Path reportsDir;
    private final String mavenCommand;
    private final List<String> extraArguments;
    private final IntFunction<List<String>> runSpecificArguments;

    public MavenRunExecutor(Path moduleDir, Path reportsDir, String mavenCommand, List<String> extraArguments,
                            IntFunction<List<String>> runSpecificArguments) {
        this.moduleDir = moduleDir;
        this.reportsDir = reportsDir;
        this.mavenCommand = mavenCommand;
        this.extraArguments = List.copyOf(extraArguments);
        this.runSpecificArguments = runSpecificArguments;
    }

    /** Deletes the reports of a previous orchestration. */
    public void cleanReports() {
        deleteRecursively(reportsDir);
    }

    @Override
    public List<ScenarioResult> execute(int run, BrowserChoice browser, List<String> onlyScenarios) {
        Path target = moduleDir.resolve("target");
        OUTPUT_DIRS.forEach(dir -> deleteRecursively(target.resolve(dir)));
        Path runDir = reportsDir.resolve("run-" + run);
        Path log = runDir.resolve("maven.log");
        List<String> command = command(run, browser, onlyScenarios);
        try {
            Files.createDirectories(runDir);
            RetryOrchestrator.say("Starting: %s (log: %s)", String.join(" ", command), log);
            Process process = new ProcessBuilder(command)
                    .directory(moduleDir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
            int exitCode = process.waitFor();
            RetryOrchestrator.say("Run %d: Maven exit code %d (non-zero is expected when scenarios fail)", run, exitCode);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start Maven: " + command, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while run " + run + " was executing", e);
        }
        OUTPUT_DIRS.forEach(dir -> copyRecursively(target.resolve(dir), runDir.resolve(dir)));
        return CucumberJsonReader.readDirectory(runDir.resolve("cucumber"));
    }

    List<String> command(int run, BrowserChoice browser, List<String> onlyScenarios) {
        List<String> command = new ArrayList<>(List.of(mavenCommand, "-B", "test",
                "-Dbrowser=" + browser.browser(),
                "-Dheadless=" + browser.headless(),
                "-Dreport.title=Run " + run + " - " + browser.label()));
        if (!onlyScenarios.isEmpty()) {
            command.add("-Dcucumber.features=" + String.join(",", onlyScenarios));
        }
        command.addAll(extraArguments);
        command.addAll(runSpecificArguments.apply(run));
        return command;
    }

    private static void deleteRecursively(Path path) {
        if (Files.notExists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete " + path, e);
        }
    }

    private static void copyRecursively(Path source, Path destination) {
        if (Files.notExists(source)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path p : paths.toList()) {
                Path copy = destination.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(copy);
                } else {
                    Files.copy(p, copy, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot copy " + source + " to " + destination, e);
        }
    }
}
```

- [ ] **Step 3: `CombinedReport.java`**

```java
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
```

- [ ] **Step 4:** `mvn -B -pl automation-core -am install` → all core tests pass (43 + 2 + 4 + 4 + 2 = 55).
Commit `feat(core): retry orchestrator with Maven run executor and combined report`.

---

### Task 5: Wire into parabank-tests and demonstrate

- [ ] **Step 1: Parent `pom.xml` pluginManagement**

```xml
                <plugin>
                    <groupId>org.codehaus.mojo</groupId>
                    <artifactId>exec-maven-plugin</artifactId>
                    <version>3.5.0</version>
                </plugin>
```

- [ ] **Step 2: `parabank-tests/pom.xml`** — property `<report.title>ParaBank Automation</report.title>`, Surefire
`<report.title>${report.title}</report.title>` (so `-Dreport.title` from the orchestrator wins), and the exec plugin:

```xml
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <configuration>
                    <mainClass>io.github.thegoyalyogesh.qa.core.retry.RetryOrchestrator</mainClass>
                    <systemProperties>
                        <systemProperty>
                            <key>retry.moduleDir</key>
                            <value>${project.basedir}</value>
                        </systemProperty>
                        <systemProperty>
                            <key>retry.reportsDir</key>
                            <value>${project.basedir}/../reports</value>
                        </systemProperty>
                    </systemProperties>
                </configuration>
            </plugin>
```

- [ ] **Step 3: Fault injection in `Hooks`** (import `java.util.regex.Pattern`)

```java
    /**
     * Fault injection to test the retry engine itself: {@code -Dinject.failure=<regex>} fails every scenario whose
     * name matches. The orchestrator passes it to run 1 only ({@code -Dretry.demo.injectFailure}), so the retry path
     * can be shown on a healthy site. Never set in normal runs.
     */
    @Before(order = 1)
    public void injectFailure(Scenario scenario) {
        String pattern = System.getProperty("inject.failure", "");
        if (!pattern.isBlank() && Pattern.compile(pattern).matcher(scenario.getName()).find()) {
            throw new AssertionError("Injected failure (testing the retry engine), pattern: " + pattern);
        }
    }
```

- [ ] **Step 4: Live, normal run** (repo root): `mvn -q install -DskipTests` then
`mvn -pl parabank-tests exec:java -Dheadless=true` → Run 1: 35 executed, 30 passed, 5 failed; cumulative 100%
(5 known defects excluded) → no retry → "PASSED", BUILD SUCCESS. `reports/run-1/` has `reports/spark.html`,
`cucumber/*.html|json`, `screenshots/`, `maven.log`; `reports/combined/index.html` exists.

- [ ] **Step 5: Live, retry demonstration:**
`mvn -pl parabank-tests exec:java -Dheadless=true "-Dretry.demo.injectFailure=customer U0[1-6] can log in and out"`
→ Run 1: 6 injected failures → 24/30 = 80% < 85% → Run 2 on Firefox with only those 6 → all pass → 100%, PASSED.
Combined report shows 6 × "Passed on retry".

- [ ] **Step 6:** Commit `feat(parabank): run the retry orchestrator with exec:java; fault injection for demonstrating retries`.

### Task 6: Docs and push
- [ ] Guide `10-retry-engine-and-reports.md`, decisions, Q&A, README, BUILD_LOG; commit; push; republish viewer.

---

## Execution notes (what changed while building, 2026-11-21)

- The plan's `-Dcucumber.features=uri:line` selection was **wrong**: property filters replace the runners' tags, so
  every runner re-ran every retried scenario (24 instead of 6). `cucumber.filter.name` has the same flaw.
  Replaced by `RetrySelection` (`-Dretry.scenarios=uri:line,...`) and `RetryAwareCucumberTests`, a runner base
  class that filters the TestNG data-provider rows. All 4 runners extend it. `RunExecutor.execute` now takes
  `List<ScenarioResult>`; `RunHistory.retryScenarios()` added.
- `RetryAwareCucumberTests.scenarios()` carries `@DataProvider` (annotations aren't inherited by overrides);
  regression test `RunnerDataProviderTest`.
- `CombinedReport` merges duplicate attempts (`toMap(..., Boolean::logicalOr)`).
- Core: `cucumber-testng` and `testng` are optional compile dependencies.
- `RetryOrchestratorTest` first scenario: 7 passing scenarios (70 → 80 → 90%), the plan's 6 gave 66.7%.
- Fault-injection pattern is `customer U0[1-6] can log in and out` (the real scenario name).
- `reports/` is git-ignored.
