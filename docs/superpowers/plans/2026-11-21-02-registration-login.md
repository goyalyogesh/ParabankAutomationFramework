# Plan 02 — Registration & Login Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Register 10 ParaBank customers from Excel in parallel (writing results back), then test login (positive, negative, known defect) and Selenium 4 tab handling, with Cucumber + TestNG, Extent reports and Log4j2.

**Architecture:** New generic pieces go into `automation-core` (Selenium 4 UI actions, Extent Cucumber plugin, screenshot util, Rest Assured `ApiClient`). Everything ParaBank-specific lives in `parabank-tests`: page objects, API client, Excel repository and registration service in `src/main`; Cucumber features, steps, hooks and runners in `src/test`. Two TestNG `<test>` blocks run in order (registration, then login), each running its scenarios in parallel via a parallel `@DataProvider`.

**Tech Stack:** Cucumber 7.34.9 (+ TestNG runner, PicoContainer DI), Rest Assured 6.2.0 (Groovy 5, works on JDK 27), ExtentReports 5.1.2, Log4j2 2.26.1, plus Plan 01 stack.

**Spec items:** F1–F6, F11 (ConcurrentHashMap registry, Set of window handles), F12 (relative locator, new tab), E8, E9.

**Verified site facts (2026-11-21):** see spec §5 plus:
- Register success: `h1` "Welcome <username>", `p` "Your account was created successfully. You are now logged in."
- Duplicate: `#customer.username.errors` = "This username already exists."; empty fields → `span.error` per field.
- Overview: `h1` "Accounts Overview", `#leftPanel p.smallText` = "Welcome First Last", link "Log Out".
- Footer: `#footerPanel` link "www.parasoft.com" → `http://www.parasoft.com/` (title contains "Parasoft").
- Login with `' OR '1'='1` → Cloudflare 403 challenge page; 300-char input → `p.error`.
- Headless Chrome and Firefox reach ParaBank without a Cloudflare challenge (spike run).

**Conventions:** commits use the user's git identity, no trailer; push to `origin master` at the end.
Packages: core `io.github.thegoyalyogesh.qa.core.*`, tests `io.github.thegoyalyogesh.qa.parabank.*`.
Paths below: `CORE=automation-core/src`, `PB=parabank-tests/src`, `PKG=io/github/thegoyalyogesh/qa`.

---

## File map

| File | Responsibility |
|---|---|
| `pom.xml` | + versions: Cucumber, Rest Assured, Extent |
| `automation-core/pom.xml` | + optional deps: cucumber-java, extentreports, rest-assured |
| `CORE/main/.../core/ui/UiDriver.java`, `SeleniumUiDriver.java` | + `value, attribute, waitVisible, waitForAnyVisible, typeBelow, openInNewTab, currentWindow, windowHandles, switchToWindow, closeCurrentWindow` |
| `CORE/main/.../core/report/ScreenshotUtil.java` | PNG files named per thread + time |
| `CORE/main/.../core/report/ExtentCucumberPlugin.java` | Cucumber event listener → Extent Spark report (steps, Base64 screenshots, known defects) |
| `CORE/main/.../core/api/ApiClient.java` | Rest Assured base request spec |
| `parabank-tests/pom.xml`, `testng-parabank.xml`, `testng-unit.xml` | Dependencies, suites, thread count |
| `PB/main/.../parabank/config/ParaBankConfig.java` | Typed settings |
| `PB/main/.../parabank/model/*` | `ParaBankUser`, `LoginCase`, `RegistrationResult`, `RegistrationStatus` |
| `PB/main/.../parabank/data/TestDataRepository.java` | Excel sheets ↔ model, write-back |
| `PB/main/.../parabank/data/UserRegistry.java` | `ConcurrentHashMap` of registered users |
| `PB/main/.../parabank/api/ParaBankApi.java` | login + account lookup via REST |
| `PB/main/.../parabank/pages/*` | `LoginPage`, `RegisterPage`, `AccountsOverviewPage`, `FooterComponent` |
| `PB/main/.../parabank/service/RegistrationService.java` | Idempotent registration logic |
| `PB/test/.../parabank/{context,hooks,steps,runners,tools,unit}` | Cucumber glue, runners, workbook tool, unit tests |
| `PB/test/resources/{config.properties,cucumber.properties,log4j2.xml,features/*.feature}` | Config and Gherkin |
| `testdata/ParaBankData.xlsx` | Generated workbook (Users, LoginNegative) |

---

### Task 1: Dependency versions

**Files:** Modify `pom.xml`, `automation-core/pom.xml`

- [ ] **Step 1: Parent `pom.xml` — add to `<properties>`**

```xml
        <cucumber.version>7.34.9</cucumber.version>
        <restassured.version>6.2.0</restassured.version>
        <extent.version>5.1.2</extent.version>
```

- [ ] **Step 2: Parent `pom.xml` — add to `<dependencyManagement><dependencies>`**

```xml
            <dependency>
                <groupId>io.cucumber</groupId>
                <artifactId>cucumber-java</artifactId>
                <version>${cucumber.version}</version>
            </dependency>
            <dependency>
                <groupId>io.cucumber</groupId>
                <artifactId>cucumber-testng</artifactId>
                <version>${cucumber.version}</version>
            </dependency>
            <dependency>
                <groupId>io.cucumber</groupId>
                <artifactId>cucumber-picocontainer</artifactId>
                <version>${cucumber.version}</version>
            </dependency>
            <dependency>
                <groupId>io.rest-assured</groupId>
                <artifactId>rest-assured</artifactId>
                <version>${restassured.version}</version>
            </dependency>
            <dependency>
                <groupId>com.aventstack</groupId>
                <artifactId>extentreports</artifactId>
                <version>${extent.version}</version>
            </dependency>
```

- [ ] **Step 3: `automation-core/pom.xml` — add optional dependencies after `log4j-api`**

```xml
        <!-- Optional: only projects that use the Cucumber/Extent/API helpers need these on their classpath -->
        <dependency>
            <groupId>io.cucumber</groupId>
            <artifactId>cucumber-java</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>com.aventstack</groupId>
            <artifactId>extentreports</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>io.rest-assured</groupId>
            <artifactId>rest-assured</artifactId>
            <optional>true</optional>
        </dependency>
```

- [ ] **Step 4: Verify:** `mvn -q -B -pl automation-core test` → exit 0 (32 tests). Commit `build: add Cucumber, Rest Assured and Extent versions`.

---

### Task 2: Selenium 4 actions in UiDriver

**Files:** Modify `CORE/main/.../core/ui/UiDriver.java`, `SeleniumUiDriver.java`, `CORE/test/.../core/ui/SeleniumUiDriverBrowserTest.java`

- [ ] **Step 1: Extend the browser test.** In `SeleniumUiDriverBrowserTest`, add these elements to `PAGE` just before `<script>`:

```html
            <div><label>First</label> <input id="first"></div>
            <div><label>Last</label> <input id="last"></div>
            <a id="lnk" href="https://example.invalid/x">external</a>
```

Add field `private String secondUrl;` and in `startBrowser()` after `pageUrl = ...`:

```java
        Path second = Files.createTempFile("ui-second", ".html");
        Files.writeString(second, "<html><head><title>Second</title></head><body>2</body></html>");
        secondUrl = second.toUri().toString();
```

Add tests (add imports `java.util.Optional`, `java.util.Set`):

```java
    @Test
    public void typeBelowUsesSelenium4RelativeLocator() {
        ui.type("id=first", "Ann");
        ui.typeBelow("id=first", "Smith");
        assertThat(ui.value("id=first")).isEqualTo("Ann");
        assertThat(ui.value("id=last")).isEqualTo("Smith");
    }

    @Test
    public void attributeReturnsRawHtmlValue() {
        assertThat(ui.attribute("id=lnk", "href")).isEqualTo("https://example.invalid/x");
    }

    @Test
    public void waitForAnyVisibleReturnsFirstVisibleLocatorOrEmpty() {
        assertThat(ui.waitForAnyVisible(Duration.ofSeconds(2), "id=hidden", "id=btn")).contains("id=btn");
        assertThat(ui.waitForAnyVisible(Duration.ofSeconds(1), "id=missing")).isEmpty();
    }

    @Test
    public void newTabLifecycleWithWindowHandleSet() {
        String parent = ui.currentWindow();
        String child = ui.openInNewTab(secondUrl);

        assertThat(ui.title()).isEqualTo("Second");
        Set<String> handles = ui.windowHandles();
        assertThat(handles).hasSize(2).contains(parent, child);

        ui.closeCurrentWindow();
        ui.switchToWindow(parent);
        assertThat(ui.windowHandles()).containsExactly(parent);
        assertThat(ui.title()).isEqualTo("Ui Test");
    }
```

- [ ] **Step 2: Run, expect compile failure:** `mvn -q -B -pl automation-core test -Dtest=SeleniumUiDriverBrowserTest -Dgroups=browser -Dcore.excludedGroups=none` → `cannot find symbol: method typeBelow`.

- [ ] **Step 3: Add to `UiDriver` (imports `java.time.Duration`, `java.util.Optional`, `java.util.Set`)**

```java
    /** Current value of an input field (DOM property {@code value}). */
    String value(String locator);

    /** Attribute exactly as written in the HTML, e.g. {@code href}. */
    String attribute(String locator, String name);

    /** Waits until the element is visible. */
    void waitVisible(String locator);

    /** Waits until any locator is visible; returns the first visible one (in argument order) or empty on timeout. */
    Optional<String> waitForAnyVisible(Duration timeout, String... locators);

    /** Selenium 4 relative locator: types into the nearest {@code input} below the anchor element. */
    void typeBelow(String anchorLocator, String text);

    /** Opens a new tab (Selenium 4 {@code WindowType.TAB}), loads the URL, switches to it and returns its handle. */
    String openInNewTab(String url);

    String currentWindow();

    /** All open tab/window handles; a Set because handles are unique. */
    Set<String> windowHandles();

    void switchToWindow(String handle);

    /** Closes only the current tab. Call {@link #switchToWindow} afterwards. */
    void closeCurrentWindow();
```

- [ ] **Step 4: Implement in `SeleniumUiDriver`** (imports `org.openqa.selenium.TimeoutException`, `org.openqa.selenium.WindowType`, `org.openqa.selenium.support.locators.RelativeLocator`, `java.util.Arrays`, `java.util.LinkedHashSet`, `java.util.Optional`, `java.util.Set`):

```java
    @Override
    public String value(String locator) {
        return visible(locator).getDomProperty("value");
    }

    @Override
    public String attribute(String locator, String name) {
        return visible(locator).getDomAttribute(name);
    }

    @Override
    public void waitVisible(String locator) {
        visible(locator);
    }

    @Override
    public Optional<String> waitForAnyVisible(Duration timeout, String... locators) {
        try {
            return Optional.of(new WebDriverWait(driver, timeout).until(d ->
                    Arrays.stream(locators).filter(this::isVisible).findFirst().orElse(null)));
        } catch (TimeoutException e) {
            return Optional.empty();
        }
    }

    @Override
    public void typeBelow(String anchorLocator, String text) {
        visible(anchorLocator);
        By below = RelativeLocator.with(By.tagName("input")).below(by(anchorLocator));
        WebElement input = wait.until(d -> d.findElement(below));
        input.clear();
        input.sendKeys(text);
    }

    @Override
    public String openInNewTab(String url) {
        driver.switchTo().newWindow(WindowType.TAB);
        driver.get(url);
        return driver.getWindowHandle();
    }

    @Override
    public String currentWindow() {
        return driver.getWindowHandle();
    }

    @Override
    public Set<String> windowHandles() {
        return new LinkedHashSet<>(driver.getWindowHandles());
    }

    @Override
    public void switchToWindow(String handle) {
        driver.switchTo().window(handle);
    }

    @Override
    public void closeCurrentWindow() {
        driver.close();
    }
```

- [ ] **Step 5: Run the browser test** (same command) → `Tests run: 9, Failures: 0`. Commit `feat(core): Selenium 4 relative locator, new-tab and window-handle actions`.

---

### Task 3: ScreenshotUtil and ExtentCucumberPlugin

**Files:** Create `CORE/main/.../core/report/ScreenshotUtil.java`, `ExtentCucumberPlugin.java`; tests `CORE/test/.../core/report/ScreenshotUtilTest.java`, `ExtentCucumberPluginTest.java`, fixture `CORE/test/.../core/report/fixture/FixtureSteps.java`, `CORE/test/resources/report-fixture/demo.feature`

- [ ] **Step 1: Failing tests**

`ScreenshotUtilTest.java`:

```java
package io.github.thegoyalyogesh.qa.core.report;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class ScreenshotUtilTest {

    @Test
    public void savesUniqueSanitisedFilesPerCall() throws IOException {
        Path dir = Files.createTempDirectory("shots");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};

        Path first = ScreenshotUtil.save(png, dir, "Register customer U01: <fail>");
        Path second = ScreenshotUtil.save(png, dir, "Register customer U01: <fail>");

        assertThat(first).isNotEqualTo(second);
        assertThat(first.getFileName().toString())
                .startsWith("Register_customer_U01_fail_")
                .contains("_t" + Thread.currentThread().threadId() + "_")
                .endsWith(".png");
        assertThat(Files.readAllBytes(first)).isEqualTo(png);
    }
}
```

`report-fixture/demo.feature`:

```gherkin
Feature: Report fixture

  Scenario: Passing scenario
    Given a step that passes

  @knownDefect
  Scenario: Failing scenario
    Given a step that passes
    When a step that fails
    Then a step that is skipped
```

`fixture/FixtureSteps.java`:

```java
package io.github.thegoyalyogesh.qa.core.report.fixture;

import io.cucumber.java.After;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Base64;

public class FixtureSteps {

    /** 1x1 transparent PNG. */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Given("a step that passes")
    public void passes() {
    }

    @When("a step that fails")
    public void fails() {
        throw new AssertionError("boom: expected failure");
    }

    @Then("a step that is skipped")
    public void skipped() {
    }

    @After
    public void afterScenario(Scenario scenario) {
        scenario.log("browser: chrome");
        if (scenario.isFailed()) {
            scenario.attach(PNG, "image/png", "failure-shot");
        }
    }
}
```

`ExtentCucumberPluginTest.java`:

```java
package io.github.thegoyalyogesh.qa.core.report;

import io.cucumber.core.cli.Main;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtentCucumberPluginTest {

    @Test
    public void writesSparkReportWithStepsScreenshotsAndKnownDefects() throws IOException {
        Path report = Files.createTempDirectory("extent").resolve("spark.html");

        byte exitStatus = Main.run(new String[]{
                "--plugin", ExtentCucumberPlugin.class.getName() + ":" + report,
                "--glue", "io.github.thegoyalyogesh.qa.core.report.fixture",
                "--monochrome",
                "classpath:report-fixture/demo.feature"
        }, Thread.currentThread().getContextClassLoader());

        assertThat(exitStatus).as("one scenario fails on purpose").isEqualTo((byte) 1);
        String html = Files.readString(report);
        assertThat(html)
                .contains("Passing scenario")
                .contains("Failing scenario")
                .contains("a step that passes")
                .contains("boom: expected failure")
                .contains("Known defect")
                .contains("browser: chrome")
                .contains("data:image/png;base64,");
    }
}
```

- [ ] **Step 2: Run, expect compile failure:** `mvn -q -B -pl automation-core test -Dtest='ScreenshotUtilTest,ExtentCucumberPluginTest'` → `cannot find symbol: class ScreenshotUtil`.

- [ ] **Step 3: `ScreenshotUtil.java`**

```java
package io.github.thegoyalyogesh.qa.core.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Saves screenshots with names that can never clash between parallel threads. */
public final class ScreenshotUtil {

    private ScreenshotUtil() {
    }

    /** Writes {@code <name>_t<threadId>_<nanoTime>.png} into {@code directory} and returns the path. */
    public static Path save(byte[] png, Path directory, String name) {
        String safe = name.replaceAll("[^A-Za-z0-9.-]+", "_").replaceAll("_+$", "");
        if (safe.length() > 80) {
            safe = safe.substring(0, 80);
        }
        String fileName = "%s_t%d_%d.png".formatted(safe, Thread.currentThread().threadId(), System.nanoTime());
        try {
            Files.createDirectories(directory);
            return Files.write(directory.resolve(fileName), png);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save screenshot " + fileName, e);
        }
    }
}
```

- [ ] **Step 4: `ExtentCucumberPlugin.java`**

```java
package io.github.thegoyalyogesh.qa.core.report;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EmbedEvent;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestCaseFinished;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestRunFinished;
import io.cucumber.plugin.event.TestStepFinished;
import io.cucumber.plugin.event.WriteEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cucumber plugin that writes an Extent Spark report: one test per scenario, one log line per step,
 * Base64 screenshots from {@code scenario.attach}, text from {@code scenario.log}, and a "Known defect"
 * category for failed scenarios tagged {@value #KNOWN_DEFECT_TAG}.
 * <p>
 * Usage: {@code plugin = "io.github.thegoyalyogesh.qa.core.report.ExtentCucumberPlugin:target/reports/spark.html"}.
 * Several runners in one JVM that use the same path share one report.
 */
public final class ExtentCucumberPlugin implements ConcurrentEventListener {

    public static final String KNOWN_DEFECT_TAG = "@knownDefect";
    public static final String KNOWN_DEFECT_CATEGORY = "Known defect";

    private static final ConcurrentHashMap<Path, ExtentReports> REPORTS = new ConcurrentHashMap<>();

    private final ExtentReports extent;
    private final ConcurrentHashMap<UUID, ExtentTest> tests = new ConcurrentHashMap<>();

    public ExtentCucumberPlugin(String outputFile) {
        Path path = Path.of(outputFile).toAbsolutePath().normalize();
        this.extent = REPORTS.computeIfAbsent(path, ExtentCucumberPlugin::createReports);
    }

    private static ExtentReports createReports(Path file) {
        String title = System.getProperty("report.title", "Automation Report");
        ExtentSparkReporter spark = new ExtentSparkReporter(file.toString());
        spark.config().setOfflineMode(true);
        spark.config().setDocumentTitle(title);
        spark.config().setReportName(title);
        ExtentReports reports = new ExtentReports();
        reports.attachReporter(spark);
        reports.setSystemInfo("OS", System.getProperty("os.name"));
        reports.setSystemInfo("Java", System.getProperty("java.version"));
        return reports;
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, this::onTestCaseStarted);
        publisher.registerHandlerFor(TestStepFinished.class, this::onTestStepFinished);
        publisher.registerHandlerFor(EmbedEvent.class, this::onEmbed);
        publisher.registerHandlerFor(WriteEvent.class, this::onWrite);
        publisher.registerHandlerFor(TestCaseFinished.class, this::onTestCaseFinished);
        publisher.registerHandlerFor(TestRunFinished.class, event -> extent.flush());
    }

    private void onTestCaseStarted(TestCaseStarted event) {
        TestCase testCase = event.getTestCase();
        ExtentTest test = extent.createTest(escape(testCase.getName()));
        testCase.getTags().stream().map(tag -> tag.substring(1)).forEach(test::assignCategory);
        tests.put(testCase.getId(), test);
    }

    private void onTestStepFinished(TestStepFinished event) {
        ExtentTest test = tests.get(event.getTestCase().getId());
        if (test == null) {
            return;
        }
        Result result = event.getResult();
        if (event.getTestStep() instanceof PickleStepTestStep step) {
            String name = escape(step.getStep().getKeyword() + step.getStep().getText());
            switch (result.getStatus()) {
                case PASSED -> test.pass(name);
                case FAILED -> {
                    test.fail(name);
                    if (result.getError() != null) {
                        test.fail(result.getError());
                    }
                }
                case SKIPPED -> test.skip(name);
                default -> test.warning(name + " (" + result.getStatus() + ")");
            }
        } else if (result.getStatus() == Status.FAILED && result.getError() != null) {
            test.fail(result.getError()); // a failing @Before/@After hook
        }
    }

    private void onEmbed(EmbedEvent event) {
        ExtentTest test = tests.get(event.getTestCase().getId());
        if (test == null) {
            return;
        }
        String name = event.getName() == null ? "attachment" : event.getName();
        if (event.getMediaType().startsWith("image/")) {
            String base64 = Base64.getEncoder().encodeToString(event.getData());
            test.info(escape(name), MediaEntityBuilder.createScreenCaptureFromBase64String(base64, name).build());
        } else {
            test.info(escape(name) + ": " + escape(new String(event.getData(), StandardCharsets.UTF_8)));
        }
    }

    private void onWrite(WriteEvent event) {
        ExtentTest test = tests.get(event.getTestCase().getId());
        if (test != null) {
            test.info(escape(event.getText()));
        }
    }

    private void onTestCaseFinished(TestCaseFinished event) {
        TestCase testCase = event.getTestCase();
        ExtentTest test = tests.remove(testCase.getId());
        if (test == null) {
            return;
        }
        if (event.getResult().getStatus() == Status.FAILED && testCase.getTags().contains(KNOWN_DEFECT_TAG)) {
            test.assignCategory(KNOWN_DEFECT_CATEGORY);
            test.info("Known defect: this failure is expected until the application bug is fixed.");
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
```

- [ ] **Step 5: Run** the same command → `Tests run: 2, Failures: 0`. Commit `feat(core): Extent Cucumber plugin with Base64 screenshots and thread-safe screenshot files`.

---

### Task 4: ApiClient

**Files:** Create `CORE/main/.../core/api/ApiClient.java`; test `CORE/test/.../core/api/ApiClientTest.java`

- [ ] **Step 1: Failing test**

```java
package io.github.thegoyalyogesh.qa.core.api;

import io.restassured.specification.QueryableRequestSpecification;
import io.restassured.specification.SpecificationQuerier;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ApiClientTest {

    @Test
    public void specCarriesBaseUriAndJsonAccept() {
        ApiClient client = new ApiClient("https://example.test/api");
        QueryableRequestSpecification spec = SpecificationQuerier.query(client.spec());

        assertThat(spec.getBaseUri()).isEqualTo("https://example.test/api");
        assertThat(spec.getHeaders().getValue("Accept")).contains("application/json");
    }
}
```

- [ ] **Step 2: Run** `mvn -q -B -pl automation-core test -Dtest=ApiClientTest` → `cannot find symbol: class ApiClient`.

- [ ] **Step 3: `ApiClient.java`**

```java
package io.github.thegoyalyogesh.qa.core.api;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/** Base for REST clients: one shared request specification (base URI, JSON) per API. */
public class ApiClient {

    private final RequestSpecification spec;

    public ApiClient(String baseUri) {
        this.spec = new RequestSpecBuilder()
                .setBaseUri(baseUri)
                .setAccept(ContentType.JSON)
                .build();
    }

    public RequestSpecification spec() {
        return spec;
    }

    /** New request using the shared spec; request and response are logged only if a validation fails. */
    public RequestSpecification request() {
        return RestAssured.given().spec(spec).log().ifValidationFails();
    }
}
```

- [ ] **Step 4: Run** → PASS. Then `mvn -q -B -pl automation-core install -DskipTests` (so `parabank-tests` sees the new core). Commit `feat(core): Rest Assured ApiClient base`.

---

### Task 5: parabank-tests module setup, config and model

**Files:** Modify `parabank-tests/pom.xml`; create `parabank-tests/testng-unit.xml`, `PB/test/resources/config.properties`, `cucumber.properties`, `log4j2.xml`, `PB/main/.../parabank/config/ParaBankConfig.java`, `model/{ParaBankUser,LoginCase,RegistrationResult,RegistrationStatus}.java`, test `PB/test/.../parabank/unit/ParaBankUserTest.java`

- [ ] **Step 1: Replace `parabank-tests/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.github.thegoyalyogesh.qa</groupId>
        <artifactId>banking-automation-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>parabank-tests</artifactId>
    <packaging>jar</packaging>
    <name>ParaBank Tests</name>

    <properties>
        <!-- mvn test -DsuiteXmlFile=testng-unit.xml   |   mvn test -Dthreads=5 -Dbrowser=firefox -Dheadless=true -->
        <suiteXmlFile>testng-parabank.xml</suiteXmlFile>
        <threads>3</threads>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.github.thegoyalyogesh.qa</groupId>
            <artifactId>automation-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.rest-assured</groupId>
            <artifactId>rest-assured</artifactId>
        </dependency>

        <dependency>
            <groupId>io.cucumber</groupId>
            <artifactId>cucumber-java</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.cucumber</groupId>
            <artifactId>cucumber-testng</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.cucumber</groupId>
            <artifactId>cucumber-picocontainer</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.aventstack</groupId>
            <artifactId>extentreports</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testng</groupId>
            <artifactId>testng</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.apache.logging.log4j</groupId>
            <artifactId>log4j-core</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.apache.logging.log4j</groupId>
            <artifactId>log4j-slf4j2-impl</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <suiteXmlFiles>
                        <suiteXmlFile>${suiteXmlFile}</suiteXmlFile>
                    </suiteXmlFiles>
                    <properties>
                        <property>
                            <name>dataproviderthreadcount</name>
                            <value>${threads}</value>
                        </property>
                    </properties>
                    <systemPropertyVariables>
                        <report.title>ParaBank Automation</report.title>
                    </systemPropertyVariables>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Resources**

`PB/test/resources/config.properties`:

```properties
# Defaults. Override with -Dkey=value or env var (KEY_NAME). See docs/guide/03-config-reader.md
app.url=https://parabank.parasoft.com/parabank
api.url=https://parabank.parasoft.com/parabank/services/bank
browser=chrome
headless=false
timeout.seconds=20
# Relative to the parabank-tests module directory (Maven runs tests there)
testdata.file=../testdata/ParaBankData.xlsx
username.prefix=qa
```

`PB/test/resources/cucumber.properties`:

```properties
cucumber.publish.quiet=true
```

`PB/test/resources/log4j2.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
    <Properties>
        <Property name="pattern">%d{HH:mm:ss.SSS} [%t] %-5level %c{1} - %msg%n</Property>
    </Properties>
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="${pattern}"/>
        </Console>
        <File name="File" fileName="target/logs/parabank.log" append="false">
            <PatternLayout pattern="${pattern}"/>
        </File>
    </Appenders>
    <Loggers>
        <Logger name="org.openqa.selenium" level="warn"/>
        <Logger name="org.apache.poi" level="warn"/>
        <Logger name="io.restassured" level="warn"/>
        <Root level="info">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="File"/>
        </Root>
    </Loggers>
</Configuration>
```

`parabank-tests/testng-unit.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<!-- Fast, browser-free checks: mvn -pl parabank-tests test -DsuiteXmlFile=testng-unit.xml -->
<suite name="ParaBank unit tests">
    <test name="Unit">
        <packages>
            <package name="io.github.thegoyalyogesh.qa.parabank.unit"/>
        </packages>
    </test>
</suite>
```

- [ ] **Step 3: Failing test `PB/test/.../parabank/unit/ParaBankUserTest.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.unit;

import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ParaBankUserTest {

    private static final Map<String, String> ROW = Map.ofEntries(
            Map.entry("UserId", "U01"), Map.entry("FirstName", "Aarav"), Map.entry("LastName", "Sharma"),
            Map.entry("Street", "12 Park Ave"), Map.entry("City", "Austin"), Map.entry("State", "TX"),
            Map.entry("ZipCode", "73301"), Map.entry("Phone", "555-201-1001"), Map.entry("SSN", "501-11-1001"),
            Map.entry("Username", ""), Map.entry("Password", " "), Map.entry("Status", ""));

    @Test
    public void fromRowMapsColumnsAndTreatsMissingAsBlank() {
        ParaBankUser user = ParaBankUser.fromRow(ROW);

        assertThat(user.userId()).isEqualTo("U01");
        assertThat(user.fullName()).isEqualTo("Aarav Sharma");
        assertThat(user.customerId()).isEmpty();
        assertThat(user.hasCredentials()).isFalse();
        assertThat(user.isRegistered()).isFalse();
    }

    @Test
    public void withMethodsReturnNewCopies() {
        ParaBankUser original = ParaBankUser.fromRow(ROW);
        ParaBankUser withCreds = original.withCredentials("qa_1", "Pw1234abcd");
        ParaBankUser registered = withCreds.withRegistration("REGISTERED", "15431", "19893");

        assertThat(original.hasCredentials()).isFalse();
        assertThat(withCreds.hasCredentials()).isTrue();
        assertThat(registered.isRegistered()).isTrue();
        assertThat(registered.customerId()).isEqualTo("15431");
        assertThat(registered.accountNumber()).isEqualTo("19893");
        assertThat(registered.username()).isEqualTo("qa_1");
    }

    @Test
    public void existingStatusAlsoCountsAsRegistered() {
        assertThat(ParaBankUser.fromRow(ROW).withRegistration("EXISTING", "1", "2").isRegistered()).isTrue();
        assertThat(ParaBankUser.fromRow(ROW).withRegistration("FAILED", "", "").isRegistered()).isFalse();
    }
}
```

Run `mvn -q -B -pl parabank-tests test -DsuiteXmlFile=testng-unit.xml` → compile failure (`package ...model does not exist`).

- [ ] **Step 4: Model**

`RegistrationStatus.java`:

```java
package io.github.thegoyalyogesh.qa.parabank.model;

public enum RegistrationStatus {
    /** Registered by this run through the UI. */
    REGISTERED,
    /** Credentials from Excel already work (checked through the REST API), so registration was skipped. */
    EXISTING,
    /** Registration scenario failed. */
    FAILED
}
```

`ParaBankUser.java`:

```java
package io.github.thegoyalyogesh.qa.parabank.model;

import java.util.Map;

/** One row of the Users sheet. Immutable: the {@code with...} methods return changed copies. */
public record ParaBankUser(String userId, String firstName, String lastName, String street, String city,
                           String state, String zipCode, String phone, String ssn,
                           String username, String password, String status,
                           String customerId, String accountNumber) {

    public static ParaBankUser fromRow(Map<String, String> row) {
        return new ParaBankUser(
                value(row, "UserId"), value(row, "FirstName"), value(row, "LastName"),
                value(row, "Street"), value(row, "City"), value(row, "State"), value(row, "ZipCode"),
                value(row, "Phone"), value(row, "SSN"), value(row, "Username"), value(row, "Password"),
                value(row, "Status"), value(row, "CustomerId"), value(row, "AccountNumber"));
    }

    private static String value(Map<String, String> row, String column) {
        String cell = row.get(column);
        return cell == null ? "" : cell.trim();
    }

    public boolean hasCredentials() {
        return !username.isBlank() && !password.isBlank();
    }

    public boolean isRegistered() {
        return RegistrationStatus.REGISTERED.name().equals(status) || RegistrationStatus.EXISTING.name().equals(status);
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public ParaBankUser withCredentials(String newUsername, String newPassword) {
        return new ParaBankUser(userId, firstName, lastName, street, city, state, zipCode, phone, ssn,
                newUsername, newPassword, status, customerId, accountNumber);
    }

    public ParaBankUser withRegistration(String newStatus, String newCustomerId, String newAccountNumber) {
        return new ParaBankUser(userId, firstName, lastName, street, city, state, zipCode, phone, ssn,
                username, password, newStatus, newCustomerId, newAccountNumber);
    }
}
```

`LoginCase.java`:

```java
package io.github.thegoyalyogesh.qa.parabank.model;

/** One row of the LoginNegative sheet, with {U01}-style placeholders already resolved. */
public record LoginCase(String caseId, String description, String username, String password, String expectedError) {
}
```

`RegistrationResult.java`:

```java
package io.github.thegoyalyogesh.qa.parabank.model;

/** Outcome of registering one customer; {@code user} carries final credentials and ids. */
public record RegistrationResult(ParaBankUser user, RegistrationStatus status, String acknowledgement) {
}
```

- [ ] **Step 5: `ParaBankConfig.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.config;

import io.github.thegoyalyogesh.qa.core.config.ConfigReader;
import io.github.thegoyalyogesh.qa.core.driver.BrowserType;

import java.nio.file.Path;
import java.time.Duration;

/** Typed view of config.properties (+ env vars and -D overrides). */
public final class ParaBankConfig {

    private static final ParaBankConfig INSTANCE = new ParaBankConfig(new ConfigReader());

    private final ConfigReader config;

    ParaBankConfig(ConfigReader config) {
        this.config = config;
    }

    public static ParaBankConfig get() {
        return INSTANCE;
    }

    public String appUrl() {
        return config.get("app.url");
    }

    public String apiUrl() {
        return config.get("api.url");
    }

    public BrowserType browser() {
        return BrowserType.from(config.get("browser", "chrome"));
    }

    public boolean headless() {
        return config.getBoolean("headless", false);
    }

    public Duration timeout() {
        return Duration.ofSeconds(config.getInt("timeout.seconds", 20));
    }

    public Path testDataFile() {
        return Path.of(config.get("testdata.file"));
    }

    public String usernamePrefix() {
        return config.get("username.prefix", "qa");
    }
}
```

- [ ] **Step 6: Run** unit suite → `Tests run: 3, Failures: 0`. Commit `feat(parabank): module setup, typed config and customer model`.

---

### Task 6: Excel repository, user registry and test-data workbook

**Files:** Create `PB/main/.../parabank/data/TestDataRepository.java`, `UserRegistry.java`, `PB/test/.../parabank/tools/CreateTestDataWorkbook.java`, tests `PB/test/.../parabank/unit/TestDataRepositoryTest.java`, `UserRegistryTest.java`; generate `testdata/ParaBankData.xlsx`

- [ ] **Step 1: Failing tests**

`TestDataRepositoryTest.java`:

```java
package io.github.thegoyalyogesh.qa.parabank.unit;

import io.github.thegoyalyogesh.qa.core.excel.ExcelUtil;
import io.github.thegoyalyogesh.qa.parabank.data.TestDataRepository;
import io.github.thegoyalyogesh.qa.parabank.model.LoginCase;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationResult;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationStatus;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestDataRepositoryTest {

    private Path file;
    private TestDataRepository repository;

    @BeforeMethod
    public void setUp() throws IOException {
        file = Files.createTempDirectory("repo").resolve("data.xlsx");
        ExcelUtil.writeSheet(file, TestDataRepository.USERS,
                List.of("UserId", "FirstName", "LastName", "Username", "Password", "Status"),
                List.of(Map.of("UserId", "U01", "FirstName", "Ann", "LastName", "Lee",
                                "Username", "qa_ann", "Password", "Pw1", "Status", "REGISTERED"),
                        Map.of("UserId", "U02", "FirstName", "Bob", "LastName", "Ray")));
        ExcelUtil.writeSheet(file, TestDataRepository.LOGIN_NEGATIVE,
                List.of("CaseId", "Description", "Username", "Password", "ExpectedError"),
                List.of(Map.of("CaseId", "N01", "Description", "Wrong password", "Username", "{U01}",
                                "Password", "bad", "ExpectedError", "could not be verified"),
                        Map.of("CaseId", "N02", "Username", "{U02}", "Password", "x")));
        repository = new TestDataRepository(file);
    }

    @Test
    public void readsUsersAndRegisteredSubset() {
        assertThat(repository.users()).extracting(ParaBankUser::userId).containsExactly("U01", "U02");
        assertThat(repository.registeredUsers()).extracting(ParaBankUser::userId).containsExactly("U01");
        assertThat(repository.user("U02").fullName()).isEqualTo("Bob Ray");
    }

    @Test
    public void unknownUserGivesClearError() {
        assertThatThrownBy(() -> repository.user("U99")).hasMessageContaining("U99");
    }

    @Test
    public void loginCaseResolvesUserPlaceholder() {
        LoginCase loginCase = repository.loginCase("N01");
        assertThat(loginCase.username()).isEqualTo("qa_ann");
        assertThat(loginCase.expectedError()).isEqualTo("could not be verified");
    }

    @Test
    public void placeholderForUserWithoutCredentialsExplainsWhatToDo() {
        assertThatThrownBy(() -> repository.loginCase("N02"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("U02")
                .hasMessageContaining("registration");
    }

    @Test
    public void recordRegistrationWritesBackToTheUserRow() {
        ParaBankUser user = repository.user("U02").withCredentials("qa_bob", "Pw2")
                .withRegistration("REGISTERED", "123", "456");
        repository.recordRegistration(new RegistrationResult(user, RegistrationStatus.REGISTERED, "created"), "CHROME");

        ParaBankUser saved = repository.user("U02");
        assertThat(saved.username()).isEqualTo("qa_bob");
        assertThat(saved.status()).isEqualTo("REGISTERED");
        assertThat(saved.customerId()).isEqualTo("123");
        assertThat(saved.accountNumber()).isEqualTo("456");
    }

    @Test
    public void recordLoginResultWritesBackToTheCaseRow() {
        repository.recordLoginResult("N01", "FAIL", "LOGGED_IN", "FIREFOX");

        Map<String, String> row = ExcelUtil.readSheet(file, TestDataRepository.LOGIN_NEGATIVE).get(0);
        assertThat(row).containsEntry("Result", "FAIL").containsEntry("ActualOutcome", "LOGGED_IN")
                .containsEntry("Browser", "FIREFOX");
    }
}
```

`UserRegistryTest.java`:

```java
package io.github.thegoyalyogesh.qa.parabank.unit;

import io.github.thegoyalyogesh.qa.parabank.data.UserRegistry;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class UserRegistryTest {

    @AfterMethod
    public void clear() {
        UserRegistry.clear();
    }

    private static ParaBankUser user(int i) {
        return ParaBankUser.fromRow(Map.of("UserId", "U%03d".formatted(i), "FirstName", "F" + i));
    }

    @Test
    public void parallelRegistrationsAreAllKeptAndSortedById() {
        IntStream.rangeClosed(1, 200).parallel().forEach(i -> UserRegistry.register(user(i)));

        assertThat(UserRegistry.size()).isEqualTo(200);
        assertThat(UserRegistry.all().get(0).userId()).isEqualTo("U001");
        assertThat(UserRegistry.all().get(199).userId()).isEqualTo("U200");
    }

    @Test
    public void findReturnsLatestVersionOfUser() {
        UserRegistry.register(user(1));
        UserRegistry.register(user(1).withCredentials("qa_new", "pw"));

        assertThat(UserRegistry.find("U001")).get().extracting(ParaBankUser::username).isEqualTo("qa_new");
        assertThat(UserRegistry.find("U999")).isEmpty();
    }
}
```

Run unit suite → compile failure (`package ...data does not exist`).

- [ ] **Step 2: `TestDataRepository.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.data;

import io.github.thegoyalyogesh.qa.core.excel.ExcelUtil;
import io.github.thegoyalyogesh.qa.parabank.model.LoginCase;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationResult;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationStatus;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads and writes the ParaBank test-data workbook. All access goes through ExcelUtil (thread-safe writes). */
public final class TestDataRepository {

    public static final String USERS = "Users";
    public static final String LOGIN_NEGATIVE = "LoginNegative";

    /** {@code {U01}} in a cell means "the username of user U01". */
    private static final Pattern USER_REFERENCE = Pattern.compile("\\{(U\\d+)}");

    private final Path file;

    public TestDataRepository(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    public List<ParaBankUser> users() {
        return ExcelUtil.readSheet(file, USERS).stream().map(ParaBankUser::fromRow).toList();
    }

    public List<ParaBankUser> registeredUsers() {
        return users().stream().filter(ParaBankUser::isRegistered).toList();
    }

    public ParaBankUser user(String userId) {
        return users().stream()
                .filter(user -> user.userId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No user '" + userId + "' in sheet " + USERS + " of " + file.toAbsolutePath()));
    }

    public LoginCase loginCase(String caseId) {
        Map<String, String> row = ExcelUtil.readSheet(file, LOGIN_NEGATIVE).stream()
                .filter(r -> caseId.equals(r.get("CaseId")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No case '" + caseId + "' in sheet " + LOGIN_NEGATIVE + " of " + file.toAbsolutePath()));
        return new LoginCase(caseId,
                row.getOrDefault("Description", ""),
                resolve(row.getOrDefault("Username", "")),
                resolve(row.getOrDefault("Password", "")),
                row.getOrDefault("ExpectedError", ""));
    }

    String resolve(String cell) {
        Matcher matcher = USER_REFERENCE.matcher(cell.trim());
        if (!matcher.matches()) {
            return cell;
        }
        ParaBankUser user = user(matcher.group(1));
        if (!user.hasCredentials()) {
            throw new IllegalStateException("Placeholder " + cell + " needs user " + matcher.group(1)
                    + " to have credentials. Run the registration suite first.");
        }
        return user.username();
    }

    public void recordRegistration(RegistrationResult result, String browser) {
        ParaBankUser user = result.user();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Username", user.username());
        values.put("Password", user.password());
        values.put("Status", result.status().name());
        values.put("Acknowledgement", result.acknowledgement());
        values.put("CustomerId", user.customerId());
        values.put("AccountNumber", user.accountNumber());
        values.put("RegisteredAt", now());
        values.put("Browser", browser);
        ExcelUtil.updateRow(file, USERS, "UserId", user.userId(), values);
    }

    public void recordRegistrationFailure(String userId, String reason, String browser) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Status", RegistrationStatus.FAILED.name());
        values.put("Acknowledgement", shorten(reason));
        values.put("RegisteredAt", now());
        values.put("Browser", browser);
        ExcelUtil.updateRow(file, USERS, "UserId", userId, values);
    }

    public void recordLoginResult(String caseId, String result, String actualOutcome, String browser) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Result", result);
        values.put("ActualOutcome", shorten(actualOutcome));
        values.put("ExecutedAt", now());
        values.put("Browser", browser);
        ExcelUtil.updateRow(file, LOGIN_NEGATIVE, "CaseId", caseId, values);
    }

    private static String shorten(String text) {
        String single = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return single.length() > 200 ? single.substring(0, 200) + "…" : single;
    }

    private static String now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
```

- [ ] **Step 3: `UserRegistry.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.data;

import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of customers registered during this JVM run, shared by all parallel threads.
 * {@link ConcurrentHashMap} gives thread-safe put/get without a global lock.
 * Plan 03 uses it to pick user A and user B for cross-user payments.
 */
public final class UserRegistry {

    private static final ConcurrentHashMap<String, ParaBankUser> USERS = new ConcurrentHashMap<>();

    private UserRegistry() {
    }

    public static void register(ParaBankUser user) {
        USERS.put(user.userId(), user);
    }

    public static Optional<ParaBankUser> find(String userId) {
        return Optional.ofNullable(USERS.get(userId));
    }

    /** Snapshot sorted by user id. */
    public static List<ParaBankUser> all() {
        return USERS.values().stream().sorted(Comparator.comparing(ParaBankUser::userId)).toList();
    }

    public static int size() {
        return USERS.size();
    }

    public static void clear() {
        USERS.clear();
    }
}
```

- [ ] **Step 4: Run** unit suite → `Tests run: 11, Failures: 0`.

- [ ] **Step 5: Workbook tool `PB/test/.../parabank/tools/CreateTestDataWorkbook.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.tools;

import io.github.thegoyalyogesh.qa.core.excel.ExcelUtil;
import io.github.thegoyalyogesh.qa.parabank.data.TestDataRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Creates testdata/ParaBankData.xlsx with 10 customers (credentials left blank so they get generated)
 * and the negative login cases. Run from the repository root:
 * <pre>
 * mvn -q -pl parabank-tests test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
 *   -Dexec.mainClass=io.github.thegoyalyogesh.qa.parabank.tools.CreateTestDataWorkbook \
 *   -Dexec.classpathScope=test -Dexec.args=testdata/ParaBankData.xlsx [-Doverwrite=true]
 * </pre>
 */
public final class CreateTestDataWorkbook {

    static final List<String> USER_HEADERS = List.of("UserId", "FirstName", "LastName", "Street", "City", "State",
            "ZipCode", "Phone", "SSN", "Username", "Password", "Status", "Acknowledgement", "CustomerId",
            "AccountNumber", "RegisteredAt", "Browser");

    static final List<String> NEGATIVE_HEADERS = List.of("CaseId", "Description", "Username", "Password",
            "ExpectedError", "Result", "ActualOutcome", "ExecutedAt", "Browser");

    private static final String[][] PEOPLE = {
            {"Aarav", "Sharma", "12 Park Ave", "Austin", "TX", "73301"},
            {"Priya", "Patel", "48 Lake St", "Seattle", "WA", "98101"},
            {"Liam", "Johnson", "7 Beacon St", "Boston", "MA", "02108"},
            {"Sofia", "Garcia", "301 Pine Rd", "Denver", "CO", "80202"},
            {"Noah", "Williams", "95 State St", "Chicago", "IL", "60601"},
            {"Ananya", "Iyer", "18 Ocean Dr", "Miami", "FL", "33101"},
            {"Mateo", "Rossi", "220 Market St", "San Francisco", "CA", "94105"},
            {"Emma", "Brown", "64 Elm St", "Portland", "OR", "97201"},
            {"Kabir", "Mehta", "5 River Rd", "Phoenix", "AZ", "85001"},
            {"Olivia", "Davis", "410 Main St", "Atlanta", "GA", "30301"}};

    private static final String NOT_VERIFIED = "The username and password could not be verified.";
    private static final String ENTER_BOTH = "Please enter a username and password.";

    private CreateTestDataWorkbook() {
    }

    public static void main(String[] args) throws IOException {
        Path file = Path.of(args.length > 0 ? args[0] : "testdata/ParaBankData.xlsx");
        if (Files.exists(file)) {
            if (!Boolean.getBoolean("overwrite")) {
                System.out.println("Workbook already exists (use -Doverwrite=true to replace): " + file.toAbsolutePath());
                return;
            }
            Files.delete(file);
        }
        ExcelUtil.writeSheet(file, TestDataRepository.USERS, USER_HEADERS, users());
        ExcelUtil.writeSheet(file, TestDataRepository.LOGIN_NEGATIVE, NEGATIVE_HEADERS, negativeCases());
        System.out.println("Created " + file.toAbsolutePath());
    }

    static List<Map<String, String>> users() {
        return IntStream.range(0, PEOPLE.length).mapToObj(i -> {
            String[] p = PEOPLE[i];
            int n = i + 1;
            Map<String, String> row = new LinkedHashMap<>();
            row.put("UserId", "U%02d".formatted(n));
            row.put("FirstName", p[0]);
            row.put("LastName", p[1]);
            row.put("Street", p[2]);
            row.put("City", p[3]);
            row.put("State", p[4]);
            row.put("ZipCode", p[5]);
            row.put("Phone", "555-201-%04d".formatted(1000 + n));
            row.put("SSN", "5%02d-11-%04d".formatted(n, 1000 + n));
            return row;
        }).toList();
    }

    static List<Map<String, String>> negativeCases() {
        return List.of(
                negative("N01", "Wrong password for a registered customer", "{U01}", "WrongPass99", NOT_VERIFIED),
                negative("N02", "Unknown username", "qa_nobody_000", "Whatever123", NOT_VERIFIED),
                negative("N03", "Empty username", "", "Secret123", ENTER_BOTH),
                negative("N04", "Empty password", "{U01}", "", ENTER_BOTH),
                negative("N05", "Empty username and password", "", "", ENTER_BOTH),
                negative("N06", "SQL-injection-like input", "' OR '1'='1", "' OR '1'='1", ""),
                negative("N07", "300-character username and password", "a".repeat(300), "a".repeat(300), ""));
    }

    private static Map<String, String> negative(String id, String description, String username, String password,
                                                String expectedError) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("CaseId", id);
        row.put("Description", description);
        row.put("Username", username);
        row.put("Password", password);
        row.put("ExpectedError", expectedError);
        return row;
    }
}
```

- [ ] **Step 6: Generate the workbook** (from repo root):

```bash
mvn -q -B -pl parabank-tests test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=io.github.thegoyalyogesh.qa.parabank.tools.CreateTestDataWorkbook \
  -Dexec.classpathScope=test -Dexec.args=testdata/ParaBankData.xlsx
```

Expected: `Created .../testdata/ParaBankData.xlsx`. Commit `feat(parabank): Excel test-data repository, ConcurrentHashMap user registry, workbook tool`.

---

### Task 7: ParaBank API client, page objects, registration service

**Files:** Create `PB/main/.../parabank/api/ParaBankApi.java`, `pages/{LoginPage,RegisterPage,AccountsOverviewPage,FooterComponent}.java`, `service/RegistrationService.java`

- [ ] **Step 1: `ParaBankApi.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.api;

import io.github.thegoyalyogesh.qa.core.api.ApiClient;
import io.restassured.response.Response;

import java.util.List;
import java.util.Optional;

/** The few ParaBank REST calls the UI tests need (the full API suite comes in Plan 06). */
public final class ParaBankApi {

    private final ApiClient client;

    public ParaBankApi(String baseUrl) {
        this.client = new ApiClient(baseUrl);
    }

    /** Customer id if the credentials work, otherwise empty. The API, unlike the UI, rejects bad credentials. */
    public Optional<Long> login(String username, String password) {
        Response response = client.request()
                .pathParam("username", username)
                .pathParam("password", password)
                .get("/login/{username}/{password}");
        boolean ok = response.statusCode() == 200 && String.valueOf(response.contentType()).contains("json");
        return ok ? Optional.of(response.jsonPath().getLong("id")) : Optional.empty();
    }

    public List<Long> accountIds(long customerId) {
        return client.request()
                .pathParam("customerId", customerId)
                .get("/customers/{customerId}/accounts")
                .then().statusCode(200)
                .extract().jsonPath().getList("id", Long.class);
    }
}
```

- [ ] **Step 2: Page objects**

`LoginPage.java`:

```java
package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;

import java.time.Duration;

/** The login form on the ParaBank home page (left panel). */
public final class LoginPage {

    public static final String USERNAME = "name=username";
    static final String PASSWORD = "name=password";
    static final String LOGIN_BUTTON = "css=input[value='Log In']";
    static final String LOG_OUT = "link=Log Out";
    static final String ERROR = "css=#rightPanel p.error";

    /** What the site did after pressing Log In. */
    public enum Outcome { LOGGED_IN, ERROR_SHOWN, NO_PARABANK_RESPONSE }

    private final UiDriver ui;
    private final String baseUrl;

    public LoginPage(UiDriver ui, String baseUrl) {
        this.ui = ui;
        this.baseUrl = baseUrl;
    }

    public LoginPage open() {
        ui.open(baseUrl + "/index.htm");
        ui.waitVisible(USERNAME);
        return this;
    }

    public Outcome login(String username, String password) {
        ui.type(USERNAME, username);
        ui.type(PASSWORD, password);
        ui.click(LOGIN_BUTTON);
        return ui.waitForAnyVisible(Duration.ofSeconds(15), LOG_OUT, ERROR)
                .map(found -> found.equals(LOG_OUT) ? Outcome.LOGGED_IN : Outcome.ERROR_SHOWN)
                .orElse(Outcome.NO_PARABANK_RESPONSE); // e.g. Cloudflare blocks SQL-like input with a 403 page
    }

    public boolean isLoggedIn() {
        return ui.isVisible(LOG_OUT);
    }

    public String errorMessage() {
        return ui.isVisible(ERROR) ? ui.text(ERROR) : "";
    }
}
```

`RegisterPage.java`:

```java
package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;

import java.time.Duration;

/** "Signing up is easy!" registration form. */
public final class RegisterPage {

    static final String FIRST_NAME = "id=customer.firstName";
    static final String LAST_NAME = "id=customer.lastName";
    static final String STREET = "id=customer.address.street";
    static final String CITY = "id=customer.address.city";
    static final String STATE = "id=customer.address.state";
    static final String ZIP = "id=customer.address.zipCode";
    static final String PHONE = "id=customer.phoneNumber";
    static final String SSN = "id=customer.ssn";
    static final String USERNAME = "id=customer.username";
    static final String PASSWORD = "id=customer.password";
    static final String CONFIRM = "id=repeatedPassword";
    static final String REGISTER_BUTTON = "css=input[value='Register']";

    static final String WELCOME_TITLE = "xpath=//div[@id='rightPanel']//h1[starts-with(normalize-space(),'Welcome')]";
    static final String SUCCESS_MESSAGE = "xpath=//div[@id='rightPanel']//h1/following-sibling::p[1]";
    static final String USERNAME_ERROR = "id=customer.username.errors";
    static final String ANY_FIELD_ERROR = "css=#rightPanel span.error";

    public enum Outcome { SUCCESS, USERNAME_TAKEN, VALIDATION_ERROR, NO_RESPONSE }

    private final UiDriver ui;
    private final String baseUrl;
    private final Duration timeout;

    public RegisterPage(UiDriver ui, String baseUrl, Duration timeout) {
        this.ui = ui;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
    }

    public RegisterPage open() {
        ui.open(baseUrl + "/register.htm");
        ui.waitVisible(FIRST_NAME);
        return this;
    }

    public Outcome register(ParaBankUser user) {
        ui.type(FIRST_NAME, user.firstName());
        // Selenium 4 relative locator: "Last Name" is the input directly below "First Name".
        ui.typeBelow(FIRST_NAME, user.lastName());
        if (!user.lastName().equals(ui.value(LAST_NAME))) {
            throw new IllegalStateException("Relative locator did not reach the Last Name field");
        }
        ui.type(STREET, user.street());
        ui.type(CITY, user.city());
        ui.type(STATE, user.state());
        ui.type(ZIP, user.zipCode());
        ui.type(PHONE, user.phone());
        ui.type(SSN, user.ssn());
        ui.type(USERNAME, user.username());
        ui.type(PASSWORD, user.password());
        ui.type(CONFIRM, user.password());
        ui.click(REGISTER_BUTTON);

        return ui.waitForAnyVisible(timeout, WELCOME_TITLE, USERNAME_ERROR, ANY_FIELD_ERROR)
                .map(found -> switch (found) {
                    case WELCOME_TITLE -> Outcome.SUCCESS;
                    case USERNAME_ERROR -> Outcome.USERNAME_TAKEN;
                    default -> Outcome.VALIDATION_ERROR;
                })
                .orElse(Outcome.NO_RESPONSE);
    }

    public String welcomeTitle() {
        return ui.text(WELCOME_TITLE);
    }

    public String successMessage() {
        return ui.text(SUCCESS_MESSAGE);
    }

    public String firstFieldError() {
        return ui.isVisible(ANY_FIELD_ERROR) ? ui.text(ANY_FIELD_ERROR) : "";
    }
}
```

`AccountsOverviewPage.java`:

```java
package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;

public final class AccountsOverviewPage {

    static final String TITLE = "xpath=//h1[normalize-space()='Accounts Overview']";
    static final String WELCOME = "css=#leftPanel p.smallText";
    static final String LOG_OUT = "link=Log Out";

    private final UiDriver ui;

    public AccountsOverviewPage(UiDriver ui) {
        this.ui = ui;
    }

    public AccountsOverviewPage waitUntilLoaded() {
        ui.waitVisible(TITLE);
        return this;
    }

    /** e.g. "Welcome Aarav Sharma". */
    public String welcomeText() {
        return ui.text(WELCOME);
    }

    public void logOut() {
        ui.click(LOG_OUT);
    }
}
```

`FooterComponent.java`:

```java
package io.github.thegoyalyogesh.qa.parabank.pages;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;

/** Footer links shown on every ParaBank page. */
public final class FooterComponent {

    private final UiDriver ui;

    public FooterComponent(UiDriver ui) {
        this.ui = ui;
    }

    public String linkUrl(String linkText) {
        return ui.attribute("xpath=//div[@id='footerPanel']//a[normalize-space()='" + linkText + "']", "href");
    }
}
```

- [ ] **Step 3: `RegistrationService.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.service;

import io.github.thegoyalyogesh.qa.core.data.TestDataGenerator;
import io.github.thegoyalyogesh.qa.parabank.api.ParaBankApi;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationResult;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationStatus;
import io.github.thegoyalyogesh.qa.parabank.pages.RegisterPage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;

/**
 * Registers one customer, idempotently:
 * <ol>
 *   <li>Credentials in Excel already work (REST login) → EXISTING, nothing registered.</li>
 *   <li>Blank credentials → generate unique username/password.</li>
 *   <li>Register through the UI; if the username is taken by someone else → new username, one retry.</li>
 *   <li>Look up customer id and first account number through the REST API.</li>
 * </ol>
 */
public final class RegistrationService {

    public static final String EXISTING_ACK = "Already registered (credentials verified through the REST API)";

    private static final Logger LOG = LogManager.getLogger(RegistrationService.class);

    private final RegisterPage page;
    private final ParaBankApi api;
    private final TestDataGenerator generator;
    private final String usernamePrefix;

    public RegistrationService(RegisterPage page, ParaBankApi api, TestDataGenerator generator, String usernamePrefix) {
        this.page = page;
        this.api = api;
        this.generator = generator;
        this.usernamePrefix = usernamePrefix;
    }

    public RegistrationResult register(ParaBankUser user) {
        if (user.hasCredentials()) {
            Optional<Long> existing = api.login(user.username(), user.password());
            if (existing.isPresent()) {
                LOG.info("{} already exists as {} (customer {})", user.userId(), user.username(), existing.get());
                return result(user, RegistrationStatus.EXISTING, EXISTING_ACK, existing.get());
            }
        }

        ParaBankUser candidate = user.hasCredentials()
                ? user
                : user.withCredentials(generator.uniqueUsername(usernamePrefix), generator.password());

        RegisterPage.Outcome outcome = page.open().register(candidate);
        if (outcome == RegisterPage.Outcome.USERNAME_TAKEN) {
            LOG.warn("{}: username {} is taken on the shared site, generating a new one", user.userId(), candidate.username());
            candidate = candidate.withCredentials(generator.uniqueUsername(usernamePrefix), candidate.password());
            outcome = page.open().register(candidate);
        }
        if (outcome != RegisterPage.Outcome.SUCCESS) {
            throw new IllegalStateException("Registration of " + user.userId() + " failed: " + outcome
                    + " " + page.firstFieldError());
        }

        String acknowledgement = page.successMessage();
        String username = candidate.username();
        long customerId = api.login(username, candidate.password()).orElseThrow(() ->
                new IllegalStateException("UI registration succeeded but REST login failed for " + username));
        LOG.info("{} registered as {} (customer {})", user.userId(), username, customerId);
        return result(candidate, RegistrationStatus.REGISTERED, acknowledgement, customerId);
    }

    private RegistrationResult result(ParaBankUser user, RegistrationStatus status, String acknowledgement, long customerId) {
        List<Long> accounts = api.accountIds(customerId);
        String accountNumber = accounts.isEmpty() ? "" : String.valueOf(accounts.get(0));
        ParaBankUser registered = user.withRegistration(status.name(), String.valueOf(customerId), accountNumber);
        return new RegistrationResult(registered, status, acknowledgement);
    }
}
```

- [ ] **Step 4: Compile:** `mvn -q -B -pl parabank-tests test-compile` → exit 0. Commit `feat(parabank): REST client, page objects and idempotent registration service`.

---

### Task 8: Cucumber wiring and the registration feature

**Files:** Create `PB/test/.../parabank/context/TestContext.java`, `hooks/Hooks.java`, `steps/RegistrationSteps.java`, `runners/{RegistrationRunner,LoginRunner}.java`, `PB/test/resources/features/registration.feature`, `parabank-tests/testng-parabank.xml`

- [ ] **Step 1: `TestContext.java`** (one instance per scenario, created by PicoContainer)

```java
package io.github.thegoyalyogesh.qa.parabank.context;

import io.github.thegoyalyogesh.qa.core.ui.UiDriver;
import io.github.thegoyalyogesh.qa.parabank.api.ParaBankApi;
import io.github.thegoyalyogesh.qa.parabank.config.ParaBankConfig;
import io.github.thegoyalyogesh.qa.parabank.data.TestDataRepository;
import io.github.thegoyalyogesh.qa.parabank.model.LoginCase;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import io.github.thegoyalyogesh.qa.parabank.pages.AccountsOverviewPage;
import io.github.thegoyalyogesh.qa.parabank.pages.FooterComponent;
import io.github.thegoyalyogesh.qa.parabank.pages.LoginPage;
import io.github.thegoyalyogesh.qa.parabank.pages.RegisterPage;

/**
 * Per-scenario state shared between hooks and step classes. Cucumber's PicoContainer creates one instance
 * per scenario and injects it into every constructor that asks for it, so parallel scenarios never share state.
 */
public class TestContext {

    private static final ParaBankConfig CONFIG = ParaBankConfig.get();
    private static final TestDataRepository REPOSITORY = new TestDataRepository(CONFIG.testDataFile());
    private static final ParaBankApi API = new ParaBankApi(CONFIG.apiUrl());

    private UiDriver ui;
    private ParaBankUser currentUser;
    private LoginCase loginCase;
    private String loginObservation = "";
    private boolean resultRecorded;
    private String parentWindow;

    public ParaBankConfig config() {
        return CONFIG;
    }

    public TestDataRepository repository() {
        return REPOSITORY;
    }

    public ParaBankApi api() {
        return API;
    }

    public UiDriver ui() {
        if (ui == null) {
            throw new IllegalStateException("Browser not started; the @Before hook should have done this");
        }
        return ui;
    }

    public boolean hasUi() {
        return ui != null;
    }

    public void setUi(UiDriver ui) {
        this.ui = ui;
    }

    public LoginPage loginPage() {
        return new LoginPage(ui(), CONFIG.appUrl());
    }

    public RegisterPage registerPage() {
        return new RegisterPage(ui(), CONFIG.appUrl(), CONFIG.timeout());
    }

    public AccountsOverviewPage overviewPage() {
        return new AccountsOverviewPage(ui());
    }

    public FooterComponent footer() {
        return new FooterComponent(ui());
    }

    public ParaBankUser currentUser() {
        return currentUser;
    }

    public void setCurrentUser(ParaBankUser currentUser) {
        this.currentUser = currentUser;
    }

    public LoginCase loginCase() {
        return loginCase;
    }

    public void setLoginCase(LoginCase loginCase) {
        this.loginCase = loginCase;
    }

    public String loginObservation() {
        return loginObservation;
    }

    public void setLoginObservation(String loginObservation) {
        this.loginObservation = loginObservation;
    }

    public boolean resultRecorded() {
        return resultRecorded;
    }

    public void markResultRecorded() {
        this.resultRecorded = true;
    }

    public String parentWindow() {
        return parentWindow;
    }

    public void setParentWindow(String parentWindow) {
        this.parentWindow = parentWindow;
    }
}
```

- [ ] **Step 2: `Hooks.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.hooks;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.github.thegoyalyogesh.qa.core.driver.DriverManager;
import io.github.thegoyalyogesh.qa.core.report.ScreenshotUtil;
import io.github.thegoyalyogesh.qa.core.ui.SeleniumUiDriver;
import io.github.thegoyalyogesh.qa.parabank.config.ParaBankConfig;
import io.github.thegoyalyogesh.qa.parabank.context.TestContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

/** Browser lifecycle per scenario, failure screenshots, and Excel write-back of failures/results. */
public class Hooks {

    private static final Logger LOG = LogManager.getLogger(Hooks.class);
    private static final Path SCREENSHOT_DIR = Path.of("target", "screenshots");

    private final TestContext ctx;

    public Hooks(TestContext ctx) {
        this.ctx = ctx;
    }

    @Before(order = 0)
    public void startBrowser(Scenario scenario) {
        ParaBankConfig config = ctx.config();
        DriverManager.start(config.browser(), config.headless());
        ctx.setUi(new SeleniumUiDriver(DriverManager.get(), config.timeout()));
        scenario.log("Browser: " + config.browser() + (config.headless() ? " (headless)" : "")
                + " | thread: " + Thread.currentThread().getName());
        LOG.info("START  {} [{}]", scenario.getName(), config.browser());
    }

    /** @After hooks run from highest to lowest order: screenshot (100) → Excel (50) → quit (0). */
    @After(order = 100)
    public void screenshotOnFailure(Scenario scenario) {
        if (scenario.isFailed() && ctx.hasUi()) {
            byte[] png = ctx.ui().screenshot();
            scenario.attach(png, "image/png", "Failure screenshot");
            Path file = ScreenshotUtil.save(png, SCREENSHOT_DIR, scenario.getName());
            LOG.error("FAILED {} -> {}", scenario.getName(), file);
        }
    }

    @After(value = "@registration", order = 50)
    public void recordRegistrationFailure(Scenario scenario) {
        if (scenario.isFailed() && !ctx.resultRecorded() && ctx.currentUser() != null) {
            ctx.repository().recordRegistrationFailure(ctx.currentUser().userId(),
                    "Scenario failed, see report: " + scenario.getName(), ctx.config().browser().name());
        }
    }

    @After(value = "@negativeLogin", order = 50)
    public void recordNegativeLoginResult(Scenario scenario) {
        if (ctx.loginCase() != null) {
            ctx.repository().recordLoginResult(ctx.loginCase().caseId(), scenario.isFailed() ? "FAIL" : "PASS",
                    ctx.loginObservation(), ctx.config().browser().name());
        }
    }

    @After(order = 0)
    public void quitBrowser(Scenario scenario) {
        DriverManager.quit();
        LOG.info("FINISH {} -> {}", scenario.getName(), scenario.getStatus());
    }
}
```

- [ ] **Step 3: `RegistrationSteps.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.thegoyalyogesh.qa.core.data.TestDataGenerator;
import io.github.thegoyalyogesh.qa.parabank.context.TestContext;
import io.github.thegoyalyogesh.qa.parabank.data.UserRegistry;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationResult;
import io.github.thegoyalyogesh.qa.parabank.model.RegistrationStatus;
import io.github.thegoyalyogesh.qa.parabank.service.RegistrationService;

import static org.assertj.core.api.Assertions.assertThat;

public class RegistrationSteps {

    static final String SUCCESS_MESSAGE = "Your account was created successfully. You are now logged in.";

    private final TestContext ctx;
    private RegistrationResult result;

    public RegistrationSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    @Given("customer {string} from the Users sheet")
    public void customerFromUsersSheet(String userId) {
        ctx.setCurrentUser(ctx.repository().user(userId));
    }

    @When("the customer is registered on ParaBank")
    public void registerCustomer() {
        RegistrationService service = new RegistrationService(ctx.registerPage(), ctx.api(),
                new TestDataGenerator(), ctx.config().usernamePrefix());
        result = service.register(ctx.currentUser());
        ctx.setCurrentUser(result.user());
        // Write back immediately, so generated credentials are never lost even if a later check fails.
        ctx.repository().recordRegistration(result, ctx.config().browser().name());
        ctx.markResultRecorded();
        UserRegistry.register(result.user());
    }

    @Then("the registration is acknowledged")
    public void registrationIsAcknowledged() {
        assertThat(result.status()).isIn(RegistrationStatus.REGISTERED, RegistrationStatus.EXISTING);
        if (result.status() == RegistrationStatus.REGISTERED) {
            assertThat(result.acknowledgement()).isEqualTo(SUCCESS_MESSAGE);
        }
    }

    @Then("the customer exists in the ParaBank backend")
    public void customerExistsInBackend() {
        ParaBankUser user = ctx.currentUser();
        assertThat(ctx.api().login(user.username(), user.password()))
                .as("REST login for %s", user.username())
                .contains(Long.parseLong(user.customerId()));
        assertThat(user.accountNumber()).as("first account number").isNotBlank();
    }

    @Then("the registration result is written back to Excel")
    public void resultWrittenBackToExcel() {
        ParaBankUser saved = ctx.repository().user(ctx.currentUser().userId());
        assertThat(saved.username()).isEqualTo(ctx.currentUser().username());
        assertThat(saved.password()).isEqualTo(ctx.currentUser().password());
        assertThat(saved.status()).isEqualTo(result.status().name());
        assertThat(saved.customerId()).isEqualTo(ctx.currentUser().customerId());
        assertThat(saved.accountNumber()).isEqualTo(ctx.currentUser().accountNumber());
    }
}
```

- [ ] **Step 4: Runners**

`RegistrationRunner.java`:

```java
package io.github.thegoyalyogesh.qa.parabank.runners;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

@CucumberOptions(
        features = "classpath:features",
        glue = {"io.github.thegoyalyogesh.qa.parabank.hooks", "io.github.thegoyalyogesh.qa.parabank.steps"},
        tags = "@registration",
        plugin = {
                "summary",
                "html:target/cucumber/registration.html",
                "json:target/cucumber/registration.json",
                "rerun:target/rerun/registration.txt",
                "io.github.thegoyalyogesh.qa.core.report.ExtentCucumberPlugin:target/reports/spark.html"
        })
public class RegistrationRunner extends AbstractTestNGCucumberTests {

    /** parallel = true: each scenario (one customer) runs on its own TestNG data-provider thread. */
    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
```

`LoginRunner.java`:

```java
package io.github.thegoyalyogesh.qa.parabank.runners;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

@CucumberOptions(
        features = "classpath:features",
        glue = {"io.github.thegoyalyogesh.qa.parabank.hooks", "io.github.thegoyalyogesh.qa.parabank.steps"},
        tags = "@login or @selenium4",
        plugin = {
                "summary",
                "html:target/cucumber/login.html",
                "json:target/cucumber/login.json",
                "rerun:target/rerun/login.txt",
                "io.github.thegoyalyogesh.qa.core.report.ExtentCucumberPlugin:target/reports/spark.html"
        })
public class LoginRunner extends AbstractTestNGCucumberTests {

    @Override
    @DataProvider(parallel = true)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
```

- [ ] **Step 5: `parabank-tests/testng-parabank.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd">
<!--
  The two <test> blocks run one after the other (login needs registered customers).
  Inside each block, scenarios run in parallel: thread count = -Dthreads (default 3, see pom.xml).
  Run: mvn -pl parabank-tests test [-Dthreads=5] [-Dbrowser=firefox] [-Dheadless=true]
-->
<suite name="ParaBank E2E" verbose="1">
    <test name="1 - Register customers from Excel">
        <classes>
            <class name="io.github.thegoyalyogesh.qa.parabank.runners.RegistrationRunner"/>
        </classes>
    </test>
    <test name="2 - Login and Selenium 4 features">
        <classes>
            <class name="io.github.thegoyalyogesh.qa.parabank.runners.LoginRunner"/>
        </classes>
    </test>
</suite>
```

- [ ] **Step 6: `features/registration.feature`**

```gherkin
@registration
Feature: Register bank customers from Excel
  Customers come from testdata/ParaBankData.xlsx, sheet "Users".
  Blank usernames and passwords are generated, and every result is written back to the same row.
  Registration is idempotent: a customer whose Excel credentials already work (checked through the REST API)
  is not registered again.

  Scenario Outline: Register customer <userId>
    Given customer "<userId>" from the Users sheet
    When the customer is registered on ParaBank
    Then the registration is acknowledged
    And the customer exists in the ParaBank backend
    And the registration result is written back to Excel

    Examples:
      | userId |
      | U01    |
      | U02    |
      | U03    |
      | U04    |
      | U05    |
      | U06    |
      | U07    |
      | U08    |
      | U09    |
      | U10    |
```

- [ ] **Step 7: Run the registration block only** (headless so it does not steal the screen):

```bash
mvn -q -B -pl automation-core install -DskipTests
mvn -B -pl parabank-tests test -Dheadless=true -Dcucumber.filter.tags=@registration
```

Expected: 10 scenarios passed; log shows 3 different `[TestNG-PoolService-*]` thread names; `testdata/ParaBankData.xlsx` Users rows have Username, Password, Status=REGISTERED, Acknowledgement, CustomerId, AccountNumber, RegisteredAt, Browser. Run again → all 10 report `EXISTING` (idempotency). Commit `feat(parabank): Cucumber+TestNG parallel registration from Excel with write-back`.

---

### Task 9: Login (positive, negative, known defect) and Selenium 4 new-tab feature

**Files:** Create `PB/test/.../parabank/steps/{LoginSteps,FooterSteps}.java`, `features/login.feature`, `features/footer.feature`

- [ ] **Step 1: `LoginSteps.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.steps;

import io.cucumber.java.Scenario;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.thegoyalyogesh.qa.parabank.context.TestContext;
import io.github.thegoyalyogesh.qa.parabank.data.UserRegistry;
import io.github.thegoyalyogesh.qa.parabank.model.LoginCase;
import io.github.thegoyalyogesh.qa.parabank.model.ParaBankUser;
import io.github.thegoyalyogesh.qa.parabank.pages.LoginPage;

import static org.assertj.core.api.Assertions.assertThat;

public class LoginSteps {

    private final TestContext ctx;
    private Scenario scenario;

    public LoginSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    @Before
    public void keepScenario(Scenario scenario) {
        this.scenario = scenario;
    }

    @Given("registered customer {string} from the Users sheet")
    public void registeredCustomer(String userId) {
        ParaBankUser user = UserRegistry.find(userId).orElseGet(() -> ctx.repository().user(userId));
        assertThat(user.isRegistered())
                .as("Customer %s must be registered first (run the registration suite)", userId)
                .isTrue();
        ctx.setCurrentUser(user);
    }

    @When("the customer logs in")
    public void customerLogsIn() {
        ParaBankUser user = ctx.currentUser();
        LoginPage.Outcome outcome = ctx.loginPage().open().login(user.username(), user.password());
        ctx.setLoginObservation(outcome.name());
    }

    @Then("the Accounts Overview page shows the customer's name")
    public void overviewShowsName() {
        String welcome = ctx.overviewPage().waitUntilLoaded().welcomeText();
        assertThat(welcome).isEqualTo("Welcome " + ctx.currentUser().fullName());
    }

    @Then("the customer can log out")
    public void customerCanLogOut() {
        ctx.overviewPage().logOut();
        ctx.ui().waitVisible(LoginPage.USERNAME);
        assertThat(ctx.loginPage().isLoggedIn()).isFalse();
    }

    @Given("login case {string} from the LoginNegative sheet")
    public void loginCase(String caseId) {
        LoginCase loginCase = ctx.repository().loginCase(caseId);
        ctx.setLoginCase(loginCase);
        scenario.log(caseId + ": " + loginCase.description());
    }

    @When("the user tries to log in with those credentials")
    public void tryToLogIn() {
        LoginCase loginCase = ctx.loginCase();
        LoginPage page = ctx.loginPage().open();
        LoginPage.Outcome outcome = page.login(loginCase.username(), loginCase.password());
        String message = page.errorMessage();
        ctx.setLoginObservation(outcome + (message.isBlank() ? "" : ": " + message));
        scenario.log("Observed: " + ctx.loginObservation());
    }

    @Then("the user is not logged in")
    public void userIsNotLoggedIn() {
        assertThat(ctx.loginPage().isLoggedIn())
                .as("Login must be rejected for %s (%s), but the Accounts Overview was shown",
                        ctx.loginCase().caseId(), ctx.loginCase().description())
                .isFalse();
    }

    @Then("the expected error message is shown")
    public void expectedErrorShown() {
        String expected = ctx.loginCase().expectedError();
        if (!expected.isBlank()) {
            assertThat(ctx.loginPage().errorMessage()).contains(expected);
        }
    }
}
```

- [ ] **Step 2: `FooterSteps.java`**

```java
package io.github.thegoyalyogesh.qa.parabank.steps;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.thegoyalyogesh.qa.parabank.context.TestContext;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class FooterSteps {

    private final TestContext ctx;
    private String childWindow;

    public FooterSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    @Given("the ParaBank home page is open")
    public void homePageOpen() {
        ctx.loginPage().open();
        ctx.setParentWindow(ctx.ui().currentWindow());
    }

    @When("the {string} footer link is opened in a new tab")
    public void openFooterLinkInNewTab(String linkText) {
        String url = ctx.footer().linkUrl(linkText);
        childWindow = ctx.ui().openInNewTab(url);
    }

    @Then("the new tab shows a page titled with {string}")
    public void newTabTitle(String expected) {
        assertThat(ctx.ui().currentWindow()).isEqualTo(childWindow);
        assertThat(ctx.ui().title()).containsIgnoringCase(expected);
    }

    @Then("{int} distinct browser tabs are open")
    public void distinctTabs(int count) {
        Set<String> handles = ctx.ui().windowHandles();
        assertThat(handles).hasSize(count).contains(ctx.parentWindow(), childWindow);
    }

    @When("the new tab is closed")
    public void closeNewTab() {
        ctx.ui().closeCurrentWindow();
        ctx.ui().switchToWindow(ctx.parentWindow());
    }

    @Then("the ParaBank page is active again")
    public void paraBankActiveAgain() {
        assertThat(ctx.ui().windowHandles()).containsExactly(ctx.parentWindow());
        assertThat(ctx.ui().title()).contains("ParaBank");
    }
}
```

- [ ] **Step 3: `features/login.feature`**

```gherkin
@login
Feature: Login
  Registered customers (from Excel) can log in and out.
  Invalid logins must be rejected; cases come from the LoginNegative sheet and results are written back.

  Scenario Outline: Registered customer <userId> can log in and out
    Given registered customer "<userId>" from the Users sheet
    When the customer logs in
    Then the Accounts Overview page shows the customer's name
    And the customer can log out

    Examples:
      | userId |
      | U01    |
      | U02    |
      | U03    |
      | U04    |
      | U05    |
      | U06    |
      | U07    |
      | U08    |
      | U09    |
      | U10    |

  @negativeLogin
  Scenario Outline: Login is rejected - <caseId>
    Given login case "<caseId>" from the LoginNegative sheet
    When the user tries to log in with those credentials
    Then the user is not logged in
    And the expected error message is shown

    @knownDefect
    Examples: Known defect - the UI accepts any credentials (the REST API rejects them correctly)
      | caseId |
      | N01    |
      | N02    |

    Examples: Input validation
      | caseId |
      | N03    |
      | N04    |
      | N05    |
      | N06    |
      | N07    |
```

- [ ] **Step 4: `features/footer.feature`**

```gherkin
@selenium4
Feature: Footer link in a new browser tab
  Uses Selenium 4 WindowType.TAB and a Set of window handles.

  Scenario: The Parasoft link opens in its own tab and the bank page stays intact
    Given the ParaBank home page is open
    When the "www.parasoft.com" footer link is opened in a new tab
    Then the new tab shows a page titled with "Parasoft"
    And 2 distinct browser tabs are open
    When the new tab is closed
    Then the ParaBank page is active again
```

- [ ] **Step 5: Full suite run** (headless): `mvn -B -pl parabank-tests test -Dheadless=true`

Expected: 10 registration (EXISTING on re-run) + 10 login + 7 negative + 1 footer = 28 scenarios; **26 pass, 2 fail (N01, N02 — `@knownDefect`)**, so Maven reports BUILD FAILURE until Plan 04 excludes known defects from the threshold. `target/reports/spark.html` shows both runners, Base64 screenshots for N01/N02 and category "Known defect". LoginNegative sheet has Result/ActualOutcome per case.

- [ ] **Step 6: Firefox run:** `mvn -B -pl parabank-tests test -Dheadless=true -Dbrowser=firefox` → same results. Commit `feat(parabank): login positive/negative with known-defect tagging and Selenium 4 new-tab scenario`.

---

### Task 10: Documentation and push

**Files:** Create `docs/guide/06-bdd-cucumber-testng.md`, `07-pages-context-and-data.md`, `08-parallel-collections-selenium4.md`; modify `docs/DECISIONS.md`, `docs/INTERVIEW_QA.md`, `docs/BUILD_LOG.md`, `README.md`, `docs/guide/01-project-overview.md`

- [ ] **Step 1:** Write guide chapters 06–08 describing the real code (flow diagrams, file names, commands, actual run results from Tasks 8–9).
- [ ] **Step 2:** Add decisions D22–D30 (Cucumber 7.34.9 instead of 8.x, Rest Assured 6/Groovy 5 for JDK 27, own Extent plugin vs adapter, PicoContainer DI, two sequential TestNG blocks, Excel-key Scenario Outlines, idempotent registration + immediate write-back, `@After` write-back for negative cases, report shared per path).
- [ ] **Step 3:** Add Q&A sections: Cucumber + TestNG parallel, DI, hooks order, idempotency, known defects, relative locators, window handles, ConcurrentHashMap, Rest Assured in UI tests.
- [ ] **Step 4:** README "How to run" section; build log entries for Tasks 1–10.
- [ ] **Step 5:** Commit `docs: Plan 02 guide chapters, decisions, interview Q&A, build log` and `git push origin master`.

---

## Execution notes (what changed while building, 2026-11-21)

- **Username format** is `qa_yyMMddHHmmss_NNN` (19 chars), not `yyyyMMdd…` (21): ParaBank rejects names over 20
  characters with "This username already exists". `TestDataGenerator`'s sequence is `static` (one per JVM).
- **N07** (300-char credentials) logs in on the live site → moved to the `@knownDefect` examples.
  Expected full run: 28 scenarios, 25 pass, 3 known-defect failures (N01, N02, N07).
- **`SiteGuard`** added to `LoginPage`/`RegisterPage` to detect the Cloudflare rate limit (`error code: 1015`);
  `LoginPage` now takes the timeout as a third constructor argument.
- **Suites** `testng-registration.xml` and `testng-login.xml` added; don't use `-Dcucumber.filter.tags` to pick a flow.
- Details and evidence: `docs/BUILD_LOG.md`, Plan 02 section.
