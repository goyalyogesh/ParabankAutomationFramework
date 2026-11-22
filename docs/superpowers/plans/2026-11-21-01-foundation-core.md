# Plan 01 — Foundation & Core Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the Maven multi-module project and the reusable `automation-core` library (config, Excel, test data, browser/driver, UI abstraction), fully unit-tested and documented.

**Architecture:** Parent POM manages versions for two modules. `automation-core` holds generic, app-agnostic utilities and is later published to JFrog Artifactory. `parabank-tests` (empty skeleton here) will hold everything ParaBank-specific. Page objects (Plan 02) will depend only on the `UiDriver` interface so the engine can be swapped to Playwright later.

**Tech Stack:** Java 21, Maven 3.9+, Selenium 4.50 (Selenium Manager downloads drivers automatically), Apache POI 5.3, TestNG 7.12, AssertJ 3.26, Log4j2 2.26. Versions match the SauceDemoExercise project that already runs on this Mac.

**Spec:** `docs/superpowers/specs/2026-11-21-parabank-automation-design.md`

**Conventions used in every task**
- Base package: `io.github.thegoyalyogesh.qa.core`
- Main code: `automation-core/src/main/java/io/github/thegoyalyogesh/qa/core/...`
- Tests: `automation-core/src/test/java/io/github/thegoyalyogesh/qa/core/...`
- Every task ends with: docs updated → full core build green → commit.
- Commits use the user's own git identity (`git config user.name/user.email`) with no AI co-author trailer.

---

## File map

| File | Responsibility |
|---|---|
| `pom.xml` | Parent: modules, Java 21, dependency & plugin versions, Artifactory coordinates |
| `automation-core/pom.xml` | Library module: deps, sources/javadoc jars, excludes `browser` test group by default |
| `parabank-tests/pom.xml` | Test module skeleton, depends on `automation-core` |
| `core/config/ConfigReader.java` | Reads settings: file < environment variable < `-D` system property |
| `core/excel/ExcelUtil.java` | Read sheet → `List<Map<String,String>>`; thread-safe row update; write sheet |
| `core/data/TestDataGenerator.java` | Unique usernames, passwords, SSN, phone, zip |
| `core/driver/BrowserType.java` | Supported browsers enum |
| `core/driver/DriverFactory.java` | Builds a Chrome/Firefox `WebDriver` with options |
| `core/driver/DriverManager.java` | One `WebDriver` per thread (`ThreadLocal`) |
| `core/ui/Locator.java` | Parses `id=…`, `name=…`, `css=…`, `xpath=…`, `link=…` |
| `core/ui/UiDriver.java` | Engine-neutral UI actions interface |
| `core/ui/SeleniumUiDriver.java` | Selenium implementation with explicit waits |
| `README.md`, `docs/DECISIONS.md`, `docs/INTERVIEW_QA.md`, `docs/guide/01..05` | Documentation written alongside the code |

---

### Task 1: Project skeleton, parent POM, modules, git hygiene

**Files:**
- Create: `.gitignore`, `pom.xml`, `automation-core/pom.xml`, `parabank-tests/pom.xml`
- Create: `docs/guide/01-project-overview.md`, `docs/guide/02-maven-multi-module.md`, `docs/DECISIONS.md`, `README.md`

- [ ] **Step 1: Create `.gitignore`**

```gitignore
# Build output
target/
test-output/
reports/
rerun/
*.log

# IDE / OS
.idea/
*.iml
.vscode/
.DS_Store

# Excel lock/temp files
~$*.xlsx
*.xlsx.tmp

# Never commit secrets
settings.xml
.env
```

- [ ] **Step 2: Create parent `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>io.github.thegoyalyogesh.qa</groupId>
    <artifactId>banking-automation-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>Banking Automation Framework (parent)</name>

    <modules>
        <module>automation-core</module>
        <module>parabank-tests</module>
    </modules>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <selenium.version>4.50.0</selenium.version>
        <poi.version>5.3.0</poi.version>
        <testng.version>7.12.0</testng.version>
        <assertj.version>3.26.3</assertj.version>
        <log4j.version>2.26.1</log4j.version>
        <surefire.version>3.5.6</surefire.version>

        <!-- JFrog Artifactory (real URL set in Plan 05; credentials live only in ~/.m2/settings.xml) -->
        <artifactory.url>https://CHANGE-ME.jfrog.io/artifactory</artifactory.url>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.seleniumhq.selenium</groupId>
                <artifactId>selenium-java</artifactId>
                <version>${selenium.version}</version>
            </dependency>
            <dependency>
                <groupId>org.apache.poi</groupId>
                <artifactId>poi-ooxml</artifactId>
                <version>${poi.version}</version>
            </dependency>
            <dependency>
                <groupId>org.testng</groupId>
                <artifactId>testng</artifactId>
                <version>${testng.version}</version>
            </dependency>
            <dependency>
                <groupId>org.assertj</groupId>
                <artifactId>assertj-core</artifactId>
                <version>${assertj.version}</version>
            </dependency>
            <!-- Logging: libraries use the API; applications/tests add core + SLF4J bridge -->
            <dependency>
                <groupId>org.apache.logging.log4j</groupId>
                <artifactId>log4j-api</artifactId>
                <version>${log4j.version}</version>
            </dependency>
            <dependency>
                <groupId>org.apache.logging.log4j</groupId>
                <artifactId>log4j-core</artifactId>
                <version>${log4j.version}</version>
            </dependency>
            <dependency>
                <groupId>org.apache.logging.log4j</groupId>
                <artifactId>log4j-slf4j2-impl</artifactId>
                <version>${log4j.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.15.0</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>${surefire.version}</version>
                    <dependencies>
                        <!-- Force the native TestNG provider so TestNG suite XML (parallel, groups) is honoured -->
                        <dependency>
                            <groupId>org.apache.maven.surefire</groupId>
                            <artifactId>surefire-testng</artifactId>
                            <version>${surefire.version}</version>
                        </dependency>
                    </dependencies>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-source-plugin</artifactId>
                    <version>3.3.1</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-javadoc-plugin</artifactId>
                    <version>3.11.1</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>

    <distributionManagement>
        <repository>
            <id>jfrog-releases</id>
            <url>${artifactory.url}/libs-release-local</url>
        </repository>
        <snapshotRepository>
            <id>jfrog-snapshots</id>
            <url>${artifactory.url}/libs-snapshot-local</url>
        </snapshotRepository>
    </distributionManagement>
</project>
```

- [ ] **Step 3: Create `automation-core/pom.xml`**

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

    <artifactId>automation-core</artifactId>
    <packaging>jar</packaging>
    <name>Automation Core (reusable library)</name>
    <description>Generic, application-agnostic test automation utilities: config, Excel, test data, browser and UI abstraction.</description>

    <properties>
        <!-- Real-browser tests are opt-in: mvn test -Dcore.excludedGroups=none -Dgroups=browser -->
        <core.excludedGroups>browser</core.excludedGroups>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.seleniumhq.selenium</groupId>
            <artifactId>selenium-java</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.logging.log4j</groupId>
            <artifactId>log4j-api</artifactId>
        </dependency>

        <!-- Test-only logging implementation (consumers of the library choose their own) -->
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
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <excludedGroups>${core.excludedGroups}</excludedGroups>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-source-plugin</artifactId>
                <executions>
                    <execution>
                        <id>attach-sources</id>
                        <goals><goal>jar-no-fork</goal></goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-javadoc-plugin</artifactId>
                <configuration>
                    <doclint>none</doclint>
                </configuration>
                <executions>
                    <execution>
                        <id>attach-javadocs</id>
                        <goals><goal>jar</goal></goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: Create `parabank-tests/pom.xml` (skeleton; Plan 02 adds Cucumber etc.)**

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

    <dependencies>
        <dependency>
            <groupId>io.github.thegoyalyogesh.qa</groupId>
            <artifactId>automation-core</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 5a: Create `automation-core/src/test/resources/log4j2-test.xml` (quiet console logging for core tests)**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{HH:mm:ss.SSS} [%t] %-5level %c{1} - %msg%n"/>
        </Console>
    </Appenders>
    <Loggers>
        <Logger name="org.openqa.selenium" level="warn"/>
        <Logger name="org.apache.poi" level="warn"/>
        <Root level="info">
            <AppenderRef ref="Console"/>
        </Root>
    </Loggers>
</Configuration>
```

- [ ] **Step 5: Verify the build works**

Run: `mvn -q -B clean package -DskipTests`
Expected: exits 0 (a "JAR will be empty" warning is normal — there is no code yet). The `-sources` and `-javadoc` jars appear once classes exist (checked at the end of Task 8).

Optional check for newer library versions (pick ones ≥ 2 weeks old):
Run: `mvn -q versions:display-dependency-updates`

- [ ] **Step 6: Write `docs/guide/01-project-overview.md`**

````markdown
# 01 — Project Overview

## What this project is
An automation testing framework for **ParaBank** (https://parabank.parasoft.com/parabank), a public demo
banking website. It tests the UI with **Selenium**, describes tests in plain English with **Cucumber
(Gherkin)**, runs them in parallel with **TestNG**, reads/writes test data in **Excel (Apache POI)**,
checks the backend with **Rest Assured**, and runs in **Jenkins**.

## The story the framework tells (end-to-end)
1. Read 10 users from `testdata/ParaBankData.xlsx` (sheet `Users`). Blank username/password → generate one.
2. Register each user on ParaBank. Write the result back into Excel (status, site message, account number).
3. Log in with each user (positive) and with bad inputs (negative).
4. Move money: between a user's own accounts (Transfer Funds) and from user A to user B (Bill Pay).
5. Confirm balances through the REST API (hybrid UI + API check).
6. If fewer than 85% of tests pass, re-run only the failures on another browser — up to 3 runs.
7. Produce one report per run plus a combined report. Jenkins runs it all.

## Two modules
| Module | What lives there | Why separate |
|---|---|---|
| `automation-core` | Generic tools: config, Excel, data generator, browser setup, UI actions | Reusable by any project; published to JFrog Artifactory |
| `parabank-tests` | ParaBank pages, Gherkin features, step definitions, runners | Only meaningful for this one application |

## Important facts about the site (checked 2026-11-21)
- It is shared by everyone on the internet and is reset from time to time → usernames must be unique,
  registration must be repeatable.
- **Real defect:** the UI logs in with *any* username/password. The REST API correctly rejects them.
  Our tests for this are tagged `@knownDefect`.
````

- [ ] **Step 7: Write `docs/guide/02-maven-multi-module.md`**

````markdown
# 02 — Maven Multi-Module Setup

## Files
- `pom.xml` (parent, `packaging=pom`): lists modules, fixes Java 21, pins every library version once
  in `<dependencyManagement>` and every plugin version in `<pluginManagement>`.
- `automation-core/pom.xml`: the library. Declares dependencies *without versions* (inherited from parent).
- `parabank-tests/pom.xml`: depends on `automation-core` like on any other library.

## Logging
Log4j2. `automation-core` depends only on `log4j-api` (a library should not choose the logging engine for
its users). Tests add `log4j-core` and `log4j-slf4j2-impl`, which also captures Selenium's SLF4J logs.

## Why a parent POM?
One place to upgrade Selenium or POI. Children can't drift to different versions.

## What `mvn package` produces for the core
- `automation-core-1.0.0-SNAPSHOT.jar` — compiled code
- `-sources.jar` — source code (IDEs show it when you Ctrl-click)
- `-javadoc.jar` — API documentation
These three files plus the POM are exactly what gets uploaded to JFrog Artifactory with `mvn deploy`.
Other teams then add one `<dependency>` block — they never copy JAR files by hand.

## `distributionManagement`
Tells `mvn deploy` *where* to upload: releases (`1.0.0`) and snapshots (`1.0.0-SNAPSHOT`) go to different
repositories. Username/password are **not** in the POM — they live in `~/.m2/settings.xml` under the
matching `<server><id>jfrog-releases</id>…` (set up in Plan 05).

## SNAPSHOT vs release
`-SNAPSHOT` = work in progress, can be overwritten. A release (`1.0.0`) is immutable.

## Useful commands
| Command | Does |
|---|---|
| `mvn clean package -DskipTests` | Build all modules without tests |
| `mvn -pl automation-core test` | Unit tests of the core only |
| `mvn -pl automation-core test -Dgroups=browser -Dcore.excludedGroups=none` | Real-browser tests of the core |
| `mvn versions:display-dependency-updates` | Show newer library versions |
````

- [ ] **Step 8: Create `docs/DECISIONS.md`**

````markdown
# Design Decisions Log

Each entry: what we decided, why, and what we rejected. Newest at the bottom.

| # | Decision | Why | Rejected alternatives |
|---|---|---|---|
| D1 | Java 21 | Current LTS used by most companies; runs on JDK 25/27 too | 17 (older), 25 (less adopted) |
| D2 | Maven multi-module (`automation-core` + `parabank-tests`) | Generic utilities become a reusable, versioned library | Single module (utilities not reusable) |
| D3 | Publish `automation-core` to JFrog Artifactory (free cloud) | Standard way companies share internal libraries; consumers add one dependency | Copying JARs; local folder repo |
| D4 | Chrome primary, Firefox secondary; visible locally, headless in Jenkins | Two engines (Blink, Gecko) catch browser-specific bugs; headless is needed on CI | IE (retired, impossible on macOS), Safari (no headless, no parallel) |
| D5 | Selenium Manager (built into Selenium 4.6+) for drivers | No extra library; downloads matching chromedriver/geckodriver automatically | WebDriverManager (extra dependency, same job) |
| D6 | One `WebDriver` per thread via `ThreadLocal` | Parallel tests must never share a browser | Static driver (breaks in parallel) |
| D7 | Page objects use a `UiDriver` interface, locators are strings | Engine can switch to Playwright (Plan 07) without rewriting pages | PageFactory `@FindBy` (ties pages to Selenium) |
| D8 | Excel rows read as `List<Map<String,String>>` keyed by header | Columns can move without breaking code; easy to stream/filter | Index-based access (breaks when columns move) |
| D9 | Excel writes: one JVM-wide lock + write to temp file then atomic rename | Parallel threads can't corrupt the workbook; a crash never leaves a half-written file | No locking (lost updates); one file per thread (hard to read) |
| D10 | Config precedence: file < environment variable < `-D` property | Same code runs locally and in Jenkins; CI overrides without editing files | Hard-coded values; separate code paths for CI |
| D11 | Unique usernames `qa_<timestamp>_<seq>` | Public shared site; names must not collide with other people or parallel runs | Fixed names (fail on second run) |
| D12 | `@knownDefect` tag for UI login-with-any-credentials bug | Tests stay honest (they fail), but don't trigger retries or hide in the 85% math | Asserting buggy behaviour (hides the defect) |
| D13 | Run 3 = headless Chrome now; Playwright engine in Phase 2 | Deliver core value first; abstraction (D7) makes the switch a config change | Playwright in Phase 1 (+20–25% effort) |
| D14 | Cumulative pass % across runs | A test that passes on retry is counted as passed; matches common retry practice | Run-1-only pass % |
| D15 | Reports: Extent Spark + Cucumber HTML per run + combined | Plain HTML, screenshots, opens anywhere and in Jenkins | Allure (needs CLI/plugin to view) |
| D16 | Log4j2; core library depends on `log4j-api` only, test module adds `log4j-core` + `log4j-slf4j2-impl` | A library must not force a logging implementation on its users; the SLF4J bridge captures Selenium's logs | `System.out` (no levels/files); bundling log4j-core in the library |
| D17 | Screenshots embedded as Base64 in Extent, plus PNG files named `<browser>_<test>_t<threadId>_<nanoTime>.png` | Parallel threads never overwrite each other's screenshots; report is a single portable file | Shared file name like `failure.png` (overwritten in parallel) |
| D18 | TestNG suite XML files picked with `mvn test -DsuiteXmlFile=...`; Surefire forced to the TestNG provider | One clear command per suite; parallel settings live in XML | Hard-coded suite in POM |
| D19 | Collections with a real job: `ConcurrentHashMap` user registry, `ArrayDeque` transfer queue, `Set` of account numbers, Streams for aggregation | Shows Collections knowledge through actual needs (thread-safe sharing, ordered processing, uniqueness) | Using them only in toy examples |
| D20 | Selenium 4 features in real flows: relative locator (Register), Actions scroll/hover (Accounts Overview), `WindowType.TAB` + handle `Set` (footer link) | Demonstrates modern Selenium in context | Separate demo tests unrelated to banking |
| D21 | Planning docs (spec, roadmap, plans) committed under `docs/superpowers/` | Reviewers see the process: requirements → design → plan → code | Keeping them local |
````

- [ ] **Step 9: Create `README.md`**

````markdown
# Banking Automation Framework (ParaBank)

Java 21 · Selenium 4 · Cucumber · TestNG · Apache POI · Rest Assured · Jenkins · JFrog Artifactory

A showcase UI + API automation framework for https://parabank.parasoft.com/parabank.

## Prerequisites
- JDK 21+ (`java -version`)
- Maven 3.9+ (`mvn -v`)
- Google Chrome and Mozilla Firefox (drivers are downloaded automatically by Selenium Manager)

## Build
```bash
mvn clean package -DskipTests          # compile everything
mvn -pl automation-core test           # core unit tests (no browser)
mvn -pl automation-core test -Dgroups=browser -Dcore.excludedGroups=none   # core tests with real browsers
```

## Structure
```
automation-core/   reusable library (config, Excel, data, driver, UI abstraction)  → JFrog Artifactory
parabank-tests/    ParaBank pages, features, steps, runners
testdata/          Excel workbook (input + results written back)
docs/              guide (read in order), decisions, interview Q&A
```

## Documentation
- Start here: [docs/guide/01-project-overview.md](docs/guide/01-project-overview.md)
- Why things are the way they are: [docs/DECISIONS.md](docs/DECISIONS.md)
- Interview preparation: [docs/INTERVIEW_QA.md](docs/INTERVIEW_QA.md)
````

- [ ] **Step 10: Commit**

```bash
git add .gitignore pom.xml automation-core/pom.xml automation-core/src/test/resources/log4j2-test.xml parabank-tests/pom.xml README.md docs/
git commit -m "build: Maven multi-module skeleton, decisions log, project guide"
```

---

### Task 2: ConfigReader

**Files:**
- Create: `automation-core/src/main/java/io/github/thegoyalyogesh/qa/core/config/ConfigReader.java`
- Test: `automation-core/src/test/java/io/github/thegoyalyogesh/qa/core/config/ConfigReaderTest.java`
- Test resource: `automation-core/src/test/resources/test-config.properties`
- Create: `docs/guide/03-config-reader.md`, `docs/INTERVIEW_QA.md`

- [ ] **Step 1: Create test resource `test-config.properties`**

```properties
app.url=https://example.test/app
threads=3
headless=false
threshold=85.5
browser.rotation=chrome, firefox ,chrome-headless
```

- [ ] **Step 2: Write the failing test**

```java
package io.github.thegoyalyogesh.qa.core.config;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConfigReaderTest {

    private static final String FILE = "test-config.properties";

    @AfterMethod
    public void clearSystemProperties() {
        System.clearProperty("threads");
        System.clearProperty("headless");
    }

    @Test
    public void readsValueFromFile() {
        ConfigReader config = new ConfigReader(FILE, Map.of());
        assertThat(config.get("app.url")).isEqualTo("https://example.test/app");
    }

    @Test
    public void environmentVariableOverridesFile() {
        ConfigReader config = new ConfigReader(FILE, Map.of("HEADLESS", "true"));
        assertThat(config.getBoolean("headless", false)).isTrue();
    }

    @Test
    public void systemPropertyOverridesEnvironmentAndFile() {
        System.setProperty("threads", "7");
        ConfigReader config = new ConfigReader(FILE, Map.of("THREADS", "5"));
        assertThat(config.getInt("threads", 1)).isEqualTo(7);
    }

    @Test
    public void defaultsAreUsedWhenKeyMissing() {
        ConfigReader config = new ConfigReader(FILE, Map.of());
        assertThat(config.get("no.such.key", "fallback")).isEqualTo("fallback");
        assertThat(config.getInt("no.such.key", 42)).isEqualTo(42);
        assertThat(config.getDouble("threshold", 0)).isEqualTo(85.5);
    }

    @Test
    public void missingRequiredKeyThrowsClearMessage() {
        ConfigReader config = new ConfigReader(FILE, Map.of());
        assertThatThrownBy(() -> config.get("no.such.key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no.such.key");
    }

    @Test
    public void listValuesAreSplitAndTrimmed() {
        ConfigReader config = new ConfigReader(FILE, Map.of());
        assertThat(config.getList("browser.rotation"))
                .containsExactly("chrome", "firefox", "chrome-headless");
    }

    @Test
    public void environmentKeyIsUpperCaseWithUnderscores() {
        assertThat(ConfigReader.toEnvKey("browser.rotation")).isEqualTo("BROWSER_ROTATION");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -q -pl automation-core test -Dtest=ConfigReaderTest`
Expected: FAIL — compilation error `cannot find symbol: class ConfigReader`.

- [ ] **Step 4: Write the implementation**

```java
package io.github.thegoyalyogesh.qa.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Reads configuration with this precedence (highest wins):
 * <ol>
 *   <li>JVM system property, e.g. {@code -Dthreads=5}</li>
 *   <li>Environment variable, key upper-cased with dots as underscores, e.g. {@code THREADS}</li>
 *   <li>Properties file on the classpath (default {@code config.properties})</li>
 * </ol>
 */
public final class ConfigReader {

    public static final String DEFAULT_FILE = "config.properties";

    private final Properties fileProperties;
    private final Map<String, String> environment;

    public ConfigReader() {
        this(DEFAULT_FILE, System.getenv());
    }

    public ConfigReader(String classpathFile, Map<String, String> environment) {
        this.fileProperties = load(classpathFile);
        this.environment = Map.copyOf(environment);
    }

    public Optional<String> find(String key) {
        String fromSystem = System.getProperty(key);
        if (fromSystem != null && !fromSystem.isBlank()) {
            return Optional.of(fromSystem.trim());
        }
        String fromEnv = environment.get(toEnvKey(key));
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Optional.of(fromEnv.trim());
        }
        return Optional.ofNullable(fileProperties.getProperty(key))
                .map(String::trim)
                .filter(value -> !value.isEmpty());
    }

    public String get(String key) {
        return find(key).orElseThrow(() -> new IllegalStateException(
                "Missing required config key '" + key + "'. Set it in config.properties, as env var "
                        + toEnvKey(key) + ", or with -D" + key + "=..."));
    }

    public String get(String key, String defaultValue) {
        return find(key).orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return find(key).map(Integer::parseInt).orElse(defaultValue);
    }

    public double getDouble(String key, double defaultValue) {
        return find(key).map(Double::parseDouble).orElse(defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return find(key).map(Boolean::parseBoolean).orElse(defaultValue);
    }

    /** Comma-separated value as a list, each item trimmed, blanks removed. */
    public List<String> getList(String key) {
        return find(key).stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    static String toEnvKey(String key) {
        return key.toUpperCase(Locale.ROOT).replace('.', '_');
    }

    private static Properties load(String classpathFile) {
        Properties properties = new Properties();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try (InputStream in = loader.getResourceAsStream(classpathFile)) {
            if (in != null) {
                properties.load(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read config file " + classpathFile, e);
        }
        return properties;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -q -pl automation-core test -Dtest=ConfigReaderTest`
Expected: PASS — `Tests run: 7, Failures: 0, Errors: 0`.

- [ ] **Step 6: Write `docs/guide/03-config-reader.md`**

````markdown
# 03 — ConfigReader (settings without editing code)

File: `automation-core/.../core/config/ConfigReader.java`

## Problem it solves
Locally you want a visible Chrome with 3 threads; Jenkins wants headless with 5 threads and maybe another
URL. We never want to edit code or commit a changed file for that.

## How it decides a value (highest wins)
1. `-Dthreads=5` on the Maven command line (Jenkins parameters end up here)
2. Environment variable `THREADS` (key upper-cased, `.` → `_`, so `browser.rotation` → `BROWSER_ROTATION`)
3. `config.properties` on the classpath (the defaults committed in git)

## API
| Method | Use |
|---|---|
| `get(key)` | Required value; throws a message that tells you the three ways to set it |
| `get(key, default)`, `getInt`, `getDouble`, `getBoolean` | Optional value with default |
| `getList(key)` | `chrome, firefox` → `["chrome","firefox"]` (Stream API: split → trim → filter) |
| `find(key)` | `Optional<String>` for custom handling |

## Design notes
- The environment map is passed into the constructor, so tests can fake env vars (`Map.of("HEADLESS","true")`).
- Returning `Optional` avoids `null` checks everywhere.
````

- [ ] **Step 7: Create `docs/INTERVIEW_QA.md` with the first section**

````markdown
# Interview Q&A — explain the framework, then survive the "what if" questions

Format: **Q** → short answer you can say out loud. "Tweak" questions change the scenario.

## Configuration

**Q: How does your framework handle different settings for local runs and Jenkins?**
A: `ConfigReader` with precedence file < environment variable < `-D` system property. Defaults live in
`config.properties`; Jenkins passes `-Dheadless=true -Dthreads=5`. No code or file change needed.

**Tweak: Tomorrow you must run against QA, UAT and PROD-like environments. What changes?**
A: Add `env` key (`-Denv=uat`) and load `config-${env}.properties` on top of the base file. Same precedence
chain; only the file name is chosen dynamically.

**Tweak: A password must not be in the properties file. How?**
A: Don't put it there. Read it from an environment variable that Jenkins fills from its Credentials store
(`withCredentials`). `ConfigReader` already reads env vars, so no code change.

**Q: Why return `Optional` from `find`?**
A: It forces the caller to decide what happens when the value is missing (default or error) instead of
getting a `NullPointerException` far away from the cause.
````

- [ ] **Step 8: Commit**

```bash
git add automation-core/src docs/guide/03-config-reader.md docs/INTERVIEW_QA.md
git commit -m "feat(core): ConfigReader with file < env < system property precedence"
```

---

### Task 3: ExcelUtil — reading sheets

**Files:**
- Create: `automation-core/src/main/java/io/github/thegoyalyogesh/qa/core/excel/ExcelUtil.java`
- Test: `automation-core/src/test/java/io/github/thegoyalyogesh/qa/core/excel/ExcelUtilReadTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.github.thegoyalyogesh.qa.core.excel;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ExcelUtilReadTest {

    private Path file;

    @BeforeMethod
    public void setUp() throws IOException {
        file = Files.createTempDirectory("excel-read").resolve("data.xlsx");
    }

    @Test
    public void writeSheetThenReadSheetReturnsRowsKeyedByHeader() {
        ExcelUtil.writeSheet(file, "Users", List.of("UserId", "FirstName"),
                List.of(Map.of("UserId", "U01", "FirstName", "Ann"),
                        Map.of("UserId", "U02", "FirstName", "Bob")));

        List<Map<String, String>> rows = ExcelUtil.readSheet(file, "Users");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("UserId", "U01").containsEntry("FirstName", "Ann");
        assertThat(rows.get(1).keySet()).containsExactly("UserId", "FirstName");
    }

    @Test
    public void blankRowsAreSkipped() {
        ExcelUtil.writeSheet(file, "Users", List.of("Name"),
                List.of(Map.of("Name", "A"), Map.of("Name", ""), Map.of("Name", "B")));

        assertThat(ExcelUtil.readSheet(file, "Users"))
                .extracting(row -> row.get("Name"))
                .containsExactly("A", "B");
    }

    @Test
    public void numericCellsAreReadAsDisplayedText() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(file)) {
            Sheet sheet = wb.createSheet("Data");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Zip");
            sheet.createRow(1).createCell(0).setCellValue(90210);
            wb.write(out);
        }

        assertThat(ExcelUtil.readSheet(file, "Data").get(0)).containsEntry("Zip", "90210");
    }

    @Test
    public void writeSheetKeepsOtherSheets() {
        ExcelUtil.writeSheet(file, "A", List.of("x"), List.of(Map.of("x", "1")));
        ExcelUtil.writeSheet(file, "B", List.of("y"), List.of(Map.of("y", "2")));

        assertThat(ExcelUtil.readSheet(file, "A").get(0)).containsEntry("x", "1");
        assertThat(ExcelUtil.readSheet(file, "B").get(0)).containsEntry("y", "2");
    }

    @Test
    public void missingSheetThrowsWithSheetName() {
        ExcelUtil.writeSheet(file, "Users", List.of("Name"), List.of(Map.of("Name", "A")));

        assertThatThrownBy(() -> ExcelUtil.readSheet(file, "Nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nope");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl automation-core test -Dtest=ExcelUtilReadTest`
Expected: FAIL — compilation error `cannot find symbol: class ExcelUtil`.

- [ ] **Step 3: Write the implementation (read + writeSheet; `updateRow` comes in Task 4)**

```java
package io.github.thegoyalyogesh.qa.core.excel;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

/**
 * Generic Excel (.xlsx) helper. Row 0 of every sheet is the header row; each data row is returned as a
 * map of header → cell text. All writes are serialised with one lock and saved via temp file + atomic move.
 */
public final class ExcelUtil {

    private static final ReentrantLock WRITE_LOCK = new ReentrantLock();
    private static final DataFormatter FORMATTER = new DataFormatter();

    private ExcelUtil() {
    }

    /** Reads all non-blank data rows of a sheet. Column order is preserved. */
    public static List<Map<String, String>> readSheet(Path file, String sheetName) {
        try (Workbook workbook = open(file)) {
            Sheet sheet = requireSheet(workbook, sheetName, file);
            List<String> headers = headers(sheet);
            return IntStream.rangeClosed(1, sheet.getLastRowNum())
                    .mapToObj(sheet::getRow)
                    .filter(Objects::nonNull)
                    .map(row -> toMap(headers, row))
                    .filter(map -> map.values().stream().anyMatch(value -> !value.isBlank()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read Excel file " + file, e);
        }
    }

    /** Creates (or replaces) one sheet; other sheets in an existing file are kept. */
    public static void writeSheet(Path file, String sheetName, List<String> headers,
                                  List<Map<String, String>> rows) {
        WRITE_LOCK.lock();
        try (Workbook workbook = Files.exists(file) ? open(file) : new XSSFWorkbook()) {
            int existing = workbook.getSheetIndex(sheetName);
            if (existing >= 0) {
                workbook.removeSheetAt(existing);
            }
            Sheet sheet = workbook.createSheet(sheetName);
            Row headerRow = sheet.createRow(0);
            IntStream.range(0, headers.size())
                    .forEach(col -> headerRow.createCell(col).setCellValue(headers.get(col)));
            IntStream.range(0, rows.size()).forEach(r -> {
                Row row = sheet.createRow(r + 1);
                IntStream.range(0, headers.size()).forEach(col ->
                        row.createCell(col).setCellValue(rows.get(r).getOrDefault(headers.get(col), "")));
            });
            save(workbook, file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write Excel file " + file, e);
        } finally {
            WRITE_LOCK.unlock();
        }
    }

    static Workbook open(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return new XSSFWorkbook(in);
        }
    }

    static Sheet requireSheet(Workbook workbook, String sheetName, Path file) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet '" + sheetName + "' not found in " + file);
        }
        return sheet;
    }

    static List<String> headers(Sheet sheet) {
        Row header = sheet.getRow(0);
        if (header == null) {
            throw new IllegalArgumentException("Sheet '" + sheet.getSheetName() + "' has no header row");
        }
        return IntStream.range(0, header.getLastCellNum())
                .mapToObj(col -> FORMATTER.formatCellValue(header.getCell(col)).trim())
                .toList();
    }

    static String cellText(Row row, int col) {
        return FORMATTER.formatCellValue(row.getCell(col)).trim();
    }

    static void save(Workbook workbook, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = parent.resolve(file.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(temp)) {
            workbook.write(out);
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static Map<String, String> toMap(List<String> headers, Row row) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int col = 0; col < headers.size(); col++) {
            String header = headers.get(col);
            if (!header.isEmpty()) {
                values.put(header, cellText(row, col));
            }
        }
        return Collections.unmodifiableMap(values);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl automation-core test -Dtest=ExcelUtilReadTest`
Expected: PASS — `Tests run: 5, Failures: 0`. No Log4j warning (log4j-core is on the test classpath).

- [ ] **Step 5: Commit**

```bash
git add automation-core/src
git commit -m "feat(core): ExcelUtil readSheet/writeSheet using POI and streams"
```

---

### Task 4: ExcelUtil — thread-safe row update (write-back)

**Files:**
- Modify: `automation-core/src/main/java/io/github/thegoyalyogesh/qa/core/excel/ExcelUtil.java` (add `updateRow`)
- Test: `automation-core/src/test/java/io/github/thegoyalyogesh/qa/core/excel/ExcelUtilUpdateTest.java`
- Create: `docs/guide/04-excel-utility.md`; Modify: `docs/INTERVIEW_QA.md`

- [ ] **Step 1: Write the failing test**

```java
package io.github.thegoyalyogesh.qa.core.excel;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ExcelUtilUpdateTest {

    private Path file;

    @BeforeMethod
    public void setUp() throws IOException {
        file = Files.createTempDirectory("excel-update").resolve("data.xlsx");
        List<Map<String, String>> rows = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> Map.of("UserId", "U" + i, "Status", ""))
                .toList();
        ExcelUtil.writeSheet(file, "Users", List.of("UserId", "Status"), rows);
    }

    @Test
    public void updatesExistingColumnOfMatchingRow() {
        ExcelUtil.updateRow(file, "Users", "UserId", "U3", Map.of("Status", "REGISTERED"));

        Map<String, String> row = ExcelUtil.readSheet(file, "Users").get(2);
        assertThat(row).containsEntry("UserId", "U3").containsEntry("Status", "REGISTERED");
    }

    @Test
    public void addsMissingColumnToHeader() {
        ExcelUtil.updateRow(file, "Users", "UserId", "U1", Map.of("AccountNumber", "13344"));

        List<Map<String, String>> rows = ExcelUtil.readSheet(file, "Users");
        assertThat(rows.get(0)).containsEntry("AccountNumber", "13344");
        assertThat(rows.get(1)).containsEntry("AccountNumber", "");
    }

    @Test
    public void unknownKeyValueThrows() {
        assertThatThrownBy(() -> ExcelUtil.updateRow(file, "Users", "UserId", "U99", Map.of("Status", "X")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("U99");
    }

    @Test
    public void unknownKeyColumnThrows() {
        assertThatThrownBy(() -> ExcelUtil.updateRow(file, "Users", "Nope", "U1", Map.of("Status", "X")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nope");
    }

    @Test
    public void concurrentUpdatesAreAllPersisted() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Future<?>> futures = IntStream.rangeClosed(1, 10)
                    .<Future<?>>mapToObj(i -> pool.submit(() ->
                            ExcelUtil.updateRow(file, "Users", "UserId", "U" + i, Map.of("Status", "DONE-" + i))))
                    .toList();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(ExcelUtil.readSheet(file, "Users"))
                .extracting(row -> row.get("Status"))
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 10).mapToObj(i -> "DONE-" + i).toList());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl automation-core test -Dtest=ExcelUtilUpdateTest`
Expected: FAIL — compilation error `cannot find symbol: method updateRow`.

- [ ] **Step 3: Add `updateRow` to `ExcelUtil` (place after `writeSheet`; add `import java.util.ArrayList;` and `import org.apache.poi.ss.usermodel.Cell;`)**

```java
    /**
     * Finds the first data row whose {@code keyColumn} equals {@code keyValue} and writes {@code updates}
     * (column → value) into it. Columns that don't exist yet are appended to the header row.
     * Thread-safe within one JVM.
     */
    public static void updateRow(Path file, String sheetName, String keyColumn, String keyValue,
                                 Map<String, String> updates) {
        WRITE_LOCK.lock();
        try (Workbook workbook = open(file)) {
            Sheet sheet = requireSheet(workbook, sheetName, file);
            Row headerRow = sheet.getRow(0);
            List<String> headers = new ArrayList<>(headers(sheet));

            int keyIndex = headers.indexOf(keyColumn);
            if (keyIndex < 0) {
                throw new IllegalArgumentException(
                        "Column '" + keyColumn + "' not found in sheet '" + sheetName + "' of " + file);
            }

            Row target = IntStream.rangeClosed(1, sheet.getLastRowNum())
                    .mapToObj(sheet::getRow)
                    .filter(Objects::nonNull)
                    .filter(row -> keyValue.equals(cellText(row, keyIndex)))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No row with " + keyColumn + "=" + keyValue + " in sheet '" + sheetName + "'"));

            updates.forEach((column, value) -> {
                int index = headers.indexOf(column);
                if (index < 0) {
                    index = headers.size();
                    headers.add(column);
                    headerRow.createCell(index).setCellValue(column);
                }
                Cell cell = target.getCell(index);
                if (cell == null) {
                    cell = target.createCell(index);
                }
                cell.setCellValue(value == null ? "" : value);
            });

            save(workbook, file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot update Excel file " + file, e);
        } finally {
            WRITE_LOCK.unlock();
        }
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -pl automation-core test -Dtest='ExcelUtil*Test'`
Expected: PASS — `Tests run: 10, Failures: 0`.

- [ ] **Step 5: Write `docs/guide/04-excel-utility.md`**

````markdown
# 04 — ExcelUtil (test data in, results out)

File: `automation-core/.../core/excel/ExcelUtil.java` · Library: Apache POI (`poi-ooxml`)

## Shape of the data
Row 0 is the header. Each data row becomes a `Map<String,String>` (header → cell text), so a sheet is a
`List<Map<String,String>>`:

| UserId | FirstName | Username | Status |
|---|---|---|---|
| U01 | Ann | | |

→ `[{UserId=U01, FirstName=Ann, Username=, Status=}]`

Code asks for `row.get("FirstName")`, never "column 2", so moving columns in Excel breaks nothing.

## Methods
| Method | What it does |
|---|---|
| `readSheet(file, sheet)` | All non-blank rows, columns in order. Uses `IntStream` → `map` → `filter` → `toList()` |
| `writeSheet(file, sheet, headers, rows)` | Creates/replaces one sheet, keeps the others |
| `updateRow(file, sheet, keyColumn, keyValue, updates)` | Finds the row (e.g. `UserId=U03`) and writes values; unknown columns are added to the header |

## Why `DataFormatter`?
Excel stores `90210` as a number; plain POI returns `90210.0`. `DataFormatter` returns the text exactly
as Excel displays it — what a tester typed.

## Parallel safety (important in interviews)
Tests run in 3+ threads and all write results to the same file. Without protection two threads read the
file, each changes one row, and the second save wipes out the first ("lost update").
- One `ReentrantLock` makes read-modify-write of the file one-at-a-time.
- Saving writes `data.xlsx.tmp` first, then renames it over the real file in one atomic step — a crash
  never leaves a half-written workbook.
- Proved by `concurrentUpdatesAreAllPersisted`: 10 threads, 10 rows, no update lost.

## Limits
- The lock protects threads in **one JVM**. Two separate Maven processes writing the same file are not
  protected — we never do that (Jenkins runs one process per job).
- Close the workbook in Excel before running; Excel's file lock can make the save fail on Windows.
````

- [ ] **Step 6: Append to `docs/INTERVIEW_QA.md`**

````markdown

## Excel data (Apache POI)

**Q: How do you read Excel data in your framework?**
A: `ExcelUtil.readSheet` returns `List<Map<String,String>>` keyed by the header row, built with the Stream
API. `DataFormatter` gives the displayed text, so `90210` doesn't become `90210.0`.

**Q: Several parallel threads write results into the same Excel. What can go wrong and how did you solve it?**
A: Lost updates and corrupted files. All writes go through one `ReentrantLock`, and saving is temp file +
atomic move. A unit test runs 10 concurrent updates and checks none is lost.

**Tweak: The business adds a new column "Branch" in the middle of the sheet. What breaks?**
A: Nothing — access is by header name, not index.

**Tweak: The sheet now has 200,000 rows and memory explodes. What do you change?**
A: `XSSFWorkbook` loads everything in memory. For reading use the streaming SAX reader
(`XSSFReader`/event API); for writing use `SXSSFWorkbook`. Or move bulk data to CSV or a database and keep
Excel for small, human-edited data.

**Tweak: Two Jenkins jobs run at the same time on the same machine and both write the file.**
A: The in-JVM lock doesn't cover two processes. Give each build its own output copy
(`results-<buildNumber>.xlsx`), or use a `FileChannel.lock()` file lock, or a database.

**Q: Why not `@DataProvider` reading the Excel directly?**
A: Cucumber owns the data flow here (features + steps), and we also *write back* results. A utility class
can be used from Cucumber steps, TestNG tests, and API tests alike.
````

- [ ] **Step 7: Commit**

```bash
git add automation-core/src docs/guide/04-excel-utility.md docs/INTERVIEW_QA.md
git commit -m "feat(core): thread-safe ExcelUtil.updateRow with atomic save"
```

---

### Task 5: TestDataGenerator

**Files:**
- Create: `automation-core/src/main/java/io/github/thegoyalyogesh/qa/core/data/TestDataGenerator.java`
- Test: `automation-core/src/test/java/io/github/thegoyalyogesh/qa/core/data/TestDataGeneratorTest.java`
- Modify: `docs/INTERVIEW_QA.md`

- [ ] **Step 1: Write the failing test**

```java
package io.github.thegoyalyogesh.qa.core.data;

import org.testng.annotations.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDataGeneratorTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-11-21T18:34:05Z"), ZoneOffset.UTC);
    private final TestDataGenerator generator = new TestDataGenerator(fixedClock, new Random(1));

    @Test
    public void usernameContainsPrefixTimestampAndSequence() {
        assertThat(generator.uniqueUsername("qa")).matches("qa_20261121183405_\\d{3}");
    }

    @Test
    public void usernamesAreUniqueEvenInParallelWithSameClock() {
        Set<String> names = IntStream.range(0, 500).parallel()
                .mapToObj(i -> generator.uniqueUsername("qa"))
                .collect(Collectors.toSet());
        assertThat(names).hasSize(500);
    }

    @Test
    public void passwordHasUpperLowerAndDigits() {
        assertThat(generator.password()).matches("(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)[A-Za-z\\d]{10}");
    }

    @Test
    public void ssnPhoneAndZipHaveUsFormats() {
        assertThat(generator.ssn()).matches("\\d{3}-\\d{2}-\\d{4}");
        assertThat(generator.phone()).matches("555-\\d{3}-\\d{4}");
        assertThat(generator.zipCode()).matches("\\d{5}");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl automation-core test -Dtest=TestDataGeneratorTest`
Expected: FAIL — compilation error `cannot find symbol: class TestDataGenerator`.

- [ ] **Step 3: Write the implementation**

```java
package io.github.thegoyalyogesh.qa.core.data;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/** Generates unique, valid-looking test data. Thread-safe. */
public final class TestDataGenerator {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";

    private final AtomicInteger sequence = new AtomicInteger();
    private final Clock clock;
    private final Random random;

    public TestDataGenerator() {
        this(Clock.systemDefaultZone(), new SecureRandom());
    }

    public TestDataGenerator(Clock clock, Random random) {
        this.clock = clock;
        this.random = random;
    }

    /** e.g. {@code qa_20261121183405_007}: timestamp separates runs, sequence separates threads. */
    public String uniqueUsername(String prefix) {
        String timestamp = LocalDateTime.now(clock).format(TIMESTAMP);
        int next = sequence.incrementAndGet() % 1000;
        return "%s_%s_%03d".formatted(prefix, timestamp, next);
    }

    /** 10 chars: 3 upper + 4 lower + 3 digits (ambiguous characters like O/0/l/1 excluded). */
    public String password() {
        return pick(UPPER, 3) + pick(LOWER, 4) + pick(DIGITS, 3);
    }

    public String ssn() {
        return "%03d-%02d-%04d".formatted(100 + random.nextInt(800), 10 + random.nextInt(90), 1000 + random.nextInt(9000));
    }

    public String phone() {
        return "555-%03d-%04d".formatted(100 + random.nextInt(900), random.nextInt(10000));
    }

    public String zipCode() {
        return "%05d".formatted(10000 + random.nextInt(90000));
    }

    private String pick(String alphabet, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl automation-core test -Dtest=TestDataGeneratorTest`
Expected: PASS — `Tests run: 4, Failures: 0`.

- [ ] **Step 5: Append to `docs/INTERVIEW_QA.md`**

````markdown

## Test data generation

**Q: How do you make sure usernames never clash on a shared public site?**
A: `prefix_<yyyyMMddHHmmss>_<seq>`. The timestamp separates runs; an `AtomicInteger` sequence separates
threads inside one run. A test generates 500 names in a parallel stream and checks all are unique.

**Q: Why inject `Clock` and `Random` into the generator?**
A: So tests are deterministic: a fixed clock gives a known timestamp and a seeded `Random` gives repeatable
values. Production uses the system clock and `SecureRandom`.

**Tweak: Two Jenkins agents start a build in the same second.**
A: Add the Jenkins `BUILD_NUMBER` or hostname to the prefix (`qa_b123`), read through `ConfigReader`.
````

- [ ] **Step 6: Commit**

```bash
git add automation-core/src docs/INTERVIEW_QA.md
git commit -m "feat(core): TestDataGenerator for unique usernames and valid test data"
```

---

### Task 6: Locator parsing

**Files:**
- Create: `automation-core/src/main/java/io/github/thegoyalyogesh/qa/core/ui/Locator.java`
- Test: `automation-core/src/test/java/io/github/thegoyalyogesh/qa/core/ui/LocatorTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.github.thegoyalyogesh.qa.core.ui;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LocatorTest {

    @Test
    public void parsesIdWithDotsInValue() {
        assertThat(Locator.parse("id=customer.firstName"))
                .isEqualTo(new Locator(Locator.Strategy.ID, "customer.firstName"));
    }

    @Test
    public void onlyFirstEqualsSignSeparatesStrategy() {
        assertThat(Locator.parse("xpath=//input[@value='Log In']"))
                .isEqualTo(new Locator(Locator.Strategy.XPATH, "//input[@value='Log In']"));
    }

    @Test
    public void strategyIsCaseInsensitiveAndTrimmed() {
        assertThat(Locator.parse(" CSS = #rightPanel h1 "))
                .isEqualTo(new Locator(Locator.Strategy.CSS, "#rightPanel h1"));
    }

    @Test
    public void supportsNameAndLinkText() {
        assertThat(Locator.parse("name=username").strategy()).isEqualTo(Locator.Strategy.NAME);
        assertThat(Locator.parse("link=Log Out").strategy()).isEqualTo(Locator.Strategy.LINK_TEXT);
    }

    @Test
    public void missingStrategyIsRejected() {
        assertThatThrownBy(() -> Locator.parse("customer.firstName"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strategy=value");
    }

    @Test
    public void unknownStrategyIsRejected() {
        assertThatThrownBy(() -> Locator.parse("foo=bar"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown locator strategy 'foo'");
    }

    @Test
    public void emptyValueIsRejected() {
        assertThatThrownBy(() -> Locator.parse("id="))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl automation-core test -Dtest=LocatorTest`
Expected: FAIL — compilation error `cannot find symbol: class Locator`.

- [ ] **Step 3: Write the implementation**

```java
package io.github.thegoyalyogesh.qa.core.ui;

import java.util.Locale;
import java.util.Objects;

/**
 * Engine-neutral element locator written as {@code strategy=value}, e.g. {@code id=customer.firstName},
 * {@code name=username}, {@code css=#rightPanel h1}, {@code xpath=//input[@value='Log In']}, {@code link=Log Out}.
 */
public record Locator(Strategy strategy, String value) {

    public enum Strategy { ID, NAME, CSS, XPATH, LINK_TEXT }

    public Locator {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(value, "value");
    }

    public static Locator parse(String raw) {
        Objects.requireNonNull(raw, "locator");
        int separator = raw.indexOf('=');
        if (separator <= 0) {
            throw new IllegalArgumentException("Locator must look like 'strategy=value' (e.g. id=login) but was: " + raw);
        }
        String prefix = raw.substring(0, separator).trim().toLowerCase(Locale.ROOT);
        String value = raw.substring(separator + 1).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Locator value is empty in: " + raw);
        }
        Strategy strategy = switch (prefix) {
            case "id" -> Strategy.ID;
            case "name" -> Strategy.NAME;
            case "css" -> Strategy.CSS;
            case "xpath" -> Strategy.XPATH;
            case "link" -> Strategy.LINK_TEXT;
            default -> throw new IllegalArgumentException(
                    "Unknown locator strategy '" + prefix + "' in: " + raw + " (use id, name, css, xpath, link)");
        };
        return new Locator(strategy, value);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl automation-core test -Dtest=LocatorTest`
Expected: PASS — `Tests run: 7, Failures: 0`.

- [ ] **Step 5: Commit**

```bash
git add automation-core/src
git commit -m "feat(core): engine-neutral Locator record with strategy=value parsing"
```

---

### Task 7: BrowserType, DriverFactory, DriverManager

**Files:**
- Create: `automation-core/src/main/java/io/github/thegoyalyogesh/qa/core/driver/BrowserType.java`
- Create: `automation-core/src/main/java/io/github/thegoyalyogesh/qa/core/driver/DriverFactory.java`
- Create: `automation-core/src/main/java/io/github/thegoyalyogesh/qa/core/driver/DriverManager.java`
- Test: `automation-core/src/test/java/io/github/thegoyalyogesh/qa/core/driver/DriverManagerTest.java` (no browser)
- Test: `automation-core/src/test/java/io/github/thegoyalyogesh/qa/core/driver/DriverSmokeTest.java` (group `browser`)

- [ ] **Step 1: Write the failing unit test (no browser needed)**

```java
package io.github.thegoyalyogesh.qa.core.driver;

import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DriverManagerTest {

    @Test
    public void browserTypeParsesCaseInsensitively() {
        assertThat(BrowserType.from(" Chrome ")).isEqualTo(BrowserType.CHROME);
        assertThat(BrowserType.from("FIREFOX")).isEqualTo(BrowserType.FIREFOX);
    }

    @Test
    public void unsupportedBrowserListsSupportedOnes() {
        assertThatThrownBy(() -> BrowserType.from("ie"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ie")
                .hasMessageContaining("CHROME");
    }

    @Test
    public void getWithoutStartExplainsWhatToDo() {
        assertThat(DriverManager.isStarted()).isFalse();
        assertThatThrownBy(DriverManager::get)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DriverManager.start");
    }

    @Test
    public void quitWithoutStartIsSafe() {
        DriverManager.quit();
        assertThat(DriverManager.isStarted()).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl automation-core test -Dtest=DriverManagerTest`
Expected: FAIL — compilation error `cannot find symbol: class BrowserType`.

- [ ] **Step 3: Write `BrowserType`**

```java
package io.github.thegoyalyogesh.qa.core.driver;

import java.util.Arrays;

public enum BrowserType {
    CHROME, FIREFOX;

    public static BrowserType from(String name) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported browser '" + name + "'. Supported: " + Arrays.toString(values())));
    }
}
```

- [ ] **Step 4: Write `DriverFactory`**

```java
package io.github.thegoyalyogesh.qa.core.driver;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.time.Duration;
import java.util.Map;

/** Creates configured browsers. Driver binaries are resolved automatically by Selenium Manager. */
public final class DriverFactory {

    private static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(60);

    private DriverFactory() {
    }

    public static WebDriver create(BrowserType type, boolean headless) {
        WebDriver driver = switch (type) {
            case CHROME -> new ChromeDriver(chromeOptions(headless));
            case FIREFOX -> new FirefoxDriver(firefoxOptions(headless));
        };
        driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT);
        return driver;
    }

    static ChromeOptions chromeOptions(boolean headless) {
        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1920,1080", "--disable-notifications");
        // Stop Chrome's "save password" / "password found in a data breach" pop-ups from blocking tests.
        options.setExperimentalOption("prefs", Map.of(
                "credentials_enable_service", false,
                "profile.password_manager_enabled", false,
                "profile.password_manager_leak_detection", false));
        return options;
    }

    static FirefoxOptions firefoxOptions(boolean headless) {
        FirefoxOptions options = new FirefoxOptions();
        if (headless) {
            options.addArguments("-headless");
        }
        options.addArguments("--width=1920", "--height=1080");
        options.addPreference("signon.rememberSignons", false);
        return options;
    }
}
```

- [ ] **Step 5: Write `DriverManager`**

```java
package io.github.thegoyalyogesh.qa.core.driver;

import org.openqa.selenium.WebDriver;

/**
 * Holds one WebDriver per thread so parallel tests never share a browser.
 * Always pair {@link #start} with {@link #quit} (e.g. in Cucumber @Before/@After hooks).
 */
public final class DriverManager {

    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    private DriverManager() {
    }

    public static void start(BrowserType type, boolean headless) {
        if (DRIVER.get() != null) {
            throw new IllegalStateException("A WebDriver is already running on thread "
                    + Thread.currentThread().getName() + ". Call DriverManager.quit() first.");
        }
        DRIVER.set(DriverFactory.create(type, headless));
    }

    public static WebDriver get() {
        WebDriver driver = DRIVER.get();
        if (driver == null) {
            throw new IllegalStateException("No WebDriver on thread " + Thread.currentThread().getName()
                    + ". Call DriverManager.start(browser, headless) first.");
        }
        return driver;
    }

    public static boolean isStarted() {
        return DRIVER.get() != null;
    }

    /** Quits the browser and clears the slot; safe to call when nothing was started. */
    public static void quit() {
        WebDriver driver = DRIVER.get();
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
        } finally {
            DRIVER.remove();
        }
    }
}
```

- [ ] **Step 6: Run unit test to verify it passes**

Run: `mvn -q -pl automation-core test -Dtest=DriverManagerTest`
Expected: PASS — `Tests run: 4, Failures: 0`.

- [ ] **Step 7: Write the real-browser smoke test**

```java
package io.github.thegoyalyogesh.qa.core.driver;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Test(groups = "browser")
public class DriverSmokeTest {

    @DataProvider
    public Object[][] browsers() {
        return new Object[][]{{BrowserType.CHROME}, {BrowserType.FIREFOX}};
    }

    @Test(dataProvider = "browsers")
    public void startsHeadlessBrowserAndOpensPage(BrowserType type) throws IOException {
        Path page = Files.createTempFile("smoke", ".html");
        Files.writeString(page, "<html><head><title>smoke-ok</title></head><body>hi</body></html>");

        DriverManager.start(type, true);
        DriverManager.get().get(page.toUri().toString());

        assertThat(DriverManager.get().getTitle()).isEqualTo("smoke-ok");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        DriverManager.quit();
    }
}
```

- [ ] **Step 8: Run the browser smoke test (needs Chrome + Firefox installed; first run downloads drivers)**

Run: `mvn -q -pl automation-core test -Dtest=DriverSmokeTest -Dgroups=browser -Dcore.excludedGroups=none`
Expected: PASS — `Tests run: 2, Failures: 0`.

- [ ] **Step 9: Run the default build to confirm browser tests are excluded by default**

Run: `mvn -q -pl automation-core test`
Expected: PASS, no browser opens; DriverSmokeTest not executed.

- [ ] **Step 10: Commit**

```bash
git add automation-core/src
git commit -m "feat(core): BrowserType, DriverFactory and ThreadLocal DriverManager"
```

---

### Task 8: UiDriver interface and SeleniumUiDriver

**Files:**
- Create: `automation-core/src/main/java/io/github/thegoyalyogesh/qa/core/ui/UiDriver.java`
- Create: `automation-core/src/main/java/io/github/thegoyalyogesh/qa/core/ui/SeleniumUiDriver.java`
- Test: `automation-core/src/test/java/io/github/thegoyalyogesh/qa/core/ui/SeleniumByMappingTest.java` (no browser)
- Test: `automation-core/src/test/java/io/github/thegoyalyogesh/qa/core/ui/SeleniumUiDriverBrowserTest.java` (group `browser`)
- Create: `docs/guide/05-driver-and-ui-layer.md`; Modify: `docs/INTERVIEW_QA.md`

- [ ] **Step 1: Write the failing mapping test**

```java
package io.github.thegoyalyogesh.qa.core.ui;

import org.openqa.selenium.By;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SeleniumByMappingTest {

    @Test
    public void mapsEveryStrategyToSeleniumBy() {
        assertThat(SeleniumUiDriver.toBy(Locator.parse("id=a"))).isEqualTo(By.id("a"));
        assertThat(SeleniumUiDriver.toBy(Locator.parse("name=b"))).isEqualTo(By.name("b"));
        assertThat(SeleniumUiDriver.toBy(Locator.parse("css=#c"))).isEqualTo(By.cssSelector("#c"));
        assertThat(SeleniumUiDriver.toBy(Locator.parse("xpath=//d"))).isEqualTo(By.xpath("//d"));
        assertThat(SeleniumUiDriver.toBy(Locator.parse("link=Log Out"))).isEqualTo(By.linkText("Log Out"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl automation-core test -Dtest=SeleniumByMappingTest`
Expected: FAIL — compilation error `cannot find symbol: class SeleniumUiDriver`.

- [ ] **Step 3: Write `UiDriver`**

```java
package io.github.thegoyalyogesh.qa.core.ui;

import java.util.List;

/**
 * Engine-neutral UI actions used by page objects. Locators are strings like {@code id=username}
 * (see {@link Locator}). Every action waits for the element to be ready before acting.
 */
public interface UiDriver {

    void open(String url);

    void click(String locator);

    /** Clears the field, then types the text. */
    void type(String locator, String text);

    /** Visible text of the element, trimmed. */
    String text(String locator);

    /** True if at least one matching element is displayed right now; never throws or waits. */
    boolean isVisible(String locator);

    void selectByVisibleText(String locator, String optionText);

    /** Visible text of the currently selected option. */
    String selectedOption(String locator);

    /** Texts of all options; waits until the list has at least one option (lists filled by JavaScript). */
    List<String> options(String locator);

    /** Waits until the element's text contains {@code expected}. */
    void waitForText(String locator, String expected);

    String title();

    String currentUrl();

    /** PNG screenshot of the current page. */
    byte[] screenshot();
}
```

- [ ] **Step 4: Write `SeleniumUiDriver`**

```java
package io.github.thegoyalyogesh.qa.core.ui;

import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/** {@link UiDriver} backed by Selenium WebDriver with explicit waits (no implicit waits anywhere). */
public class SeleniumUiDriver implements UiDriver {

    private final WebDriver driver;
    private final WebDriverWait wait;

    public SeleniumUiDriver(WebDriver driver, Duration timeout) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, timeout);
    }

    @Override
    public void open(String url) {
        driver.get(url);
    }

    @Override
    public void click(String locator) {
        wait.until(ExpectedConditions.elementToBeClickable(by(locator))).click();
    }

    @Override
    public void type(String locator, String text) {
        WebElement element = visible(locator);
        element.clear();
        element.sendKeys(text);
    }

    @Override
    public String text(String locator) {
        return visible(locator).getText().trim();
    }

    @Override
    public boolean isVisible(String locator) {
        try {
            return driver.findElements(by(locator)).stream().anyMatch(WebElement::isDisplayed);
        } catch (StaleElementReferenceException e) {
            return false;
        }
    }

    @Override
    public void selectByVisibleText(String locator, String optionText) {
        waitForOptions(locator);
        new Select(visible(locator)).selectByVisibleText(optionText);
    }

    @Override
    public String selectedOption(String locator) {
        return new Select(visible(locator)).getFirstSelectedOption().getText().trim();
    }

    @Override
    public List<String> options(String locator) {
        waitForOptions(locator);
        return new Select(visible(locator)).getOptions().stream()
                .map(WebElement::getText)
                .map(String::trim)
                .toList();
    }

    @Override
    public void waitForText(String locator, String expected) {
        wait.until(ExpectedConditions.textToBePresentInElementLocated(by(locator), expected));
    }

    @Override
    public String title() {
        return driver.getTitle();
    }

    @Override
    public String currentUrl() {
        return driver.getCurrentUrl();
    }

    @Override
    public byte[] screenshot() {
        return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
    }

    static By toBy(Locator locator) {
        return switch (locator.strategy()) {
            case ID -> By.id(locator.value());
            case NAME -> By.name(locator.value());
            case CSS -> By.cssSelector(locator.value());
            case XPATH -> By.xpath(locator.value());
            case LINK_TEXT -> By.linkText(locator.value());
        };
    }

    private By by(String locator) {
        return toBy(Locator.parse(locator));
    }

    private WebElement visible(String locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(by(locator)));
    }

    private void waitForOptions(String locator) {
        By by = by(locator);
        wait.until(d -> !new Select(d.findElement(by)).getOptions().isEmpty());
    }
}
```

- [ ] **Step 5: Run mapping test to verify it passes**

Run: `mvn -q -pl automation-core test -Dtest=SeleniumByMappingTest`
Expected: PASS — `Tests run: 1, Failures: 0`.

- [ ] **Step 6: Write the real-browser test**

```java
package io.github.thegoyalyogesh.qa.core.ui;

import io.github.thegoyalyogesh.qa.core.driver.BrowserType;
import io.github.thegoyalyogesh.qa.core.driver.DriverManager;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Test(groups = "browser")
public class SeleniumUiDriverBrowserTest {

    private static final String PAGE = """
            <html><head><title>Ui Test</title></head><body>
            <input id="name" value="old">
            <button id="btn" onclick="document.getElementById('out').textContent='Hello ' + document.getElementById('name').value">Go</button>
            <div id="out"></div>
            <select id="sel"></select>
            <div id="hidden" style="display:none">secret</div>
            <div id="late"></div>
            <script>
              setTimeout(function () {
                var sel = document.getElementById('sel');
                ['Alpha', 'Beta'].forEach(function (t) { var o = document.createElement('option'); o.text = t; sel.add(o); });
                document.getElementById('late').textContent = 'ready';
              }, 700);
            </script>
            </body></html>
            """;

    private UiDriver ui;
    private String pageUrl;

    @BeforeClass(alwaysRun = true)
    public void startBrowser() throws IOException {
        Path page = Files.createTempFile("ui-test", ".html");
        Files.writeString(page, PAGE);
        pageUrl = page.toUri().toString();
        DriverManager.start(BrowserType.CHROME, true);
        ui = new SeleniumUiDriver(DriverManager.get(), Duration.ofSeconds(5));
    }

    @BeforeMethod(alwaysRun = true)
    public void openPage() {
        ui.open(pageUrl);
    }

    @Test
    public void typeClearsFieldThenClickUpdatesText() {
        ui.type("id=name", "Yogesh");
        ui.click("css=#btn");
        assertThat(ui.text("id=out")).isEqualTo("Hello Yogesh");
    }

    @Test
    public void optionsWaitForJavascriptFilledSelect() {
        assertThat(ui.options("id=sel")).containsExactly("Alpha", "Beta");
        ui.selectByVisibleText("id=sel", "Beta");
        assertThat(ui.selectedOption("id=sel")).isEqualTo("Beta");
    }

    @Test
    public void visibilityChecksNeverThrow() {
        assertThat(ui.isVisible("id=btn")).isTrue();
        assertThat(ui.isVisible("id=hidden")).isFalse();
        assertThat(ui.isVisible("id=missing")).isFalse();
    }

    @Test
    public void waitForTextAndPageInfo() {
        ui.waitForText("id=late", "ready");
        assertThat(ui.title()).isEqualTo("Ui Test");
        assertThat(ui.currentUrl()).startsWith("file:");
    }

    @Test
    public void screenshotIsPng() {
        byte[] png = ui.screenshot();
        assertThat(png).hasSizeGreaterThan(100);
        assertThat(png[1]).isEqualTo((byte) 'P');
    }

    @AfterClass(alwaysRun = true)
    public void quitBrowser() {
        DriverManager.quit();
    }
}
```

- [ ] **Step 7: Run the browser test**

Run: `mvn -q -pl automation-core test -Dtest=SeleniumUiDriverBrowserTest -Dgroups=browser -Dcore.excludedGroups=none`
Expected: PASS — `Tests run: 5, Failures: 0`.

- [ ] **Step 8: Write `docs/guide/05-driver-and-ui-layer.md`**

````markdown
# 05 — Browser, Driver and UI Layer

Package: `automation-core/.../core/driver` and `.../core/ui`

## The layers (bottom to top)
```
Page objects (Plan 02)      LoginPage.login(user)          ← knows ParaBank, not Selenium
        │ uses
UiDriver (interface)        click("name=username")         ← engine-neutral contract
        │ implemented by
SeleniumUiDriver            WebDriverWait + By.name(...)   ← all Selenium details live here
        │ uses
DriverManager (ThreadLocal) one WebDriver per thread
        │ creates via
DriverFactory               ChromeOptions / FirefoxOptions, headless flag
```

## DriverFactory
- `create(CHROME|FIREFOX, headless)`; drivers are downloaded by **Selenium Manager** (built into Selenium 4.6+).
- Chrome: `--headless=new`, 1920×1080, password-manager pop-ups disabled (they block clicks after login).
- Firefox: `-headless`, 1920×1080, "remember password" disabled.

## DriverManager and ThreadLocal
With 3 parallel threads there are 3 browsers. `ThreadLocal<WebDriver>` gives each thread its own slot, so
`DriverManager.get()` always returns *this* thread's browser. `quit()` calls `remove()` — important because
test runners reuse threads; a leftover driver would leak into the next test.

## Locator strings
`id=…`, `name=…`, `css=…`, `xpath=…`, `link=…`. Parsed once by `Locator.parse` with clear error messages.
Strings (not `By`) keep page objects free of Selenium, so Plan 07 can add a `PlaywrightUiDriver`.

## Waiting strategy
Only **explicit waits** (`WebDriverWait`), never implicit waits — mixing both causes unpredictable delays.
Each action waits for the right condition: clickable before click, visible before type/read, "has
options" before using a dropdown (ParaBank fills account lists with JavaScript after page load).

## Browser tests are opt-in
They are in TestNG group `browser`, excluded by default so `mvn test` is fast and works without browsers:
`mvn -pl automation-core test -Dgroups=browser -Dcore.excludedGroups=none`
````

- [ ] **Step 9: Append to `docs/INTERVIEW_QA.md`**

````markdown

## Browser & driver management

**Q: How do you run tests in parallel without browsers interfering?**
A: `DriverManager` stores the driver in a `ThreadLocal`, so each thread has its own browser. Start in a
before-hook, `quit()` + `ThreadLocal.remove()` in an after-hook.

**Tweak: You forgot `ThreadLocal.remove()`. What happens?**
A: Thread pools reuse threads; the next test on that thread may get a dead/old driver ("session not
found") and memory leaks over long runs.

**Q: How are browser drivers (chromedriver/geckodriver) managed?**
A: Selenium Manager, built into Selenium 4.6+, detects the installed browser version and downloads the
matching driver. No WebDriverManager library or manual downloads.

**Tweak: Jenkins agent has no internet, so Selenium Manager can't download drivers.**
A: Pre-install drivers on the agent and point to them with `-Dwebdriver.chrome.driver=/path`, or use a
Selenium Grid / Docker images that bundle browsers and drivers.

**Q: Implicit vs explicit waits — what do you use and why?**
A: Explicit only (`WebDriverWait` with conditions per action). Mixing both can multiply wait times and
makes timeouts unpredictable. `Thread.sleep` is never used.

**Q: Why do your page objects use a `UiDriver` interface instead of `WebDriver` / `@FindBy`?**
A: Dependency inversion: pages depend on an abstraction. The same pages can run on Selenium today and
Playwright later by switching the implementation in config — no page rewrites.

**Tweak: The company says "we are moving to Playwright". What is the effort?**
A: Implement `PlaywrightUiDriver` (one class, ~12 methods) and map `Locator` strategies to Playwright
selectors. Page objects, steps and features stay unchanged.

**Tweak: Run on Selenium Grid / cloud (BrowserStack) instead of local browsers.**
A: Only `DriverFactory` changes: return `new RemoteWebDriver(gridUrl, options)` when `-Dgrid.url` is set.
````

- [ ] **Step 10: Run the full core suite (unit + browser) as a final check**

Run: `mvn -q -pl automation-core test -Dcore.excludedGroups=none`
Expected: PASS — all tests (unit + browser), `Failures: 0, Errors: 0`.

Run: `mvn -q -B clean install -DskipTests`
Expected: exit 0; `automation-core` jar, sources and javadoc installed into `~/.m2/repository/io/github/thegoyalyogesh/qa/automation-core/1.0.0-SNAPSHOT/`.

- [ ] **Step 11: Commit**

```bash
git add automation-core/src docs/guide/05-driver-and-ui-layer.md docs/INTERVIEW_QA.md
git commit -m "feat(core): UiDriver abstraction with Selenium implementation and explicit waits"
```

---

## Definition of done for Plan 01
- `mvn clean install` green; core unit tests pass without a browser; browser tests pass on Chrome and Firefox.
- `docs/guide/01–05`, `docs/DECISIONS.md` (D1–D15), `docs/INTERVIEW_QA.md` (4 sections) exist and match the code.
- 8 commits on `master`.
- Walk-through with Yogesh: explain each class and run the tests together before starting Plan 02.
