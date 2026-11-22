# Banking Automation Framework (ParaBank)

Java 21 · Selenium 4 · Playwright · Cucumber · TestNG · Apache POI · Rest Assured · OpenAPI contract validation ·
Log4j2 · ExtentReports · Jenkins · JFrog Artifactory (pending)

A showcase UI + API automation framework for https://parabank.parasoft.com/parabank. Every design choice is explained
in the docs, so the whole framework can be walked through in an interview.

## What it does
- **Excel-driven registration** of 10 customers, with write-back of credentials, customer ids, account numbers and
  results. Idempotent: customers who can still log in are skipped.
- **Login** positive (10 customers) and negative (7 cases from Excel), plus Selenium 4 features (relative locator,
  Actions API, new tab / window handles).
- **Transfers**: between own accounts, customer A → B through Bill Pay and REST, and a chain of transfers. Every UI
  action is cross-checked through the REST API (hybrid tests).
- **REST API suite**: 22 operations, each response validated against ParaBank's OpenAPI spec, plus functional and
  negative tests. It found 7 real API defects.
- **Two UI engines** behind one `UiDriver` interface: Selenium or Playwright, chosen by configuration.
- **Retry engine**: run 1 executes everything. If the pass rate (known defects excluded) is below 85%, only the failed
  scenarios run again on the next browser: Selenium Chrome → Selenium Firefox → Playwright Chrome. You get one report
  per run plus a combined report.
- **Parallel** scenarios (TestNG data providers, 3 threads by default), thread-safe Excel and reports.
- **Jenkins** pipeline configured as code (JCasC + Job DSL), parameters validated against command injection.

## Prerequisites
- JDK 21+ (`java -version`) and Maven 3.9+ (`mvn -v`)
- Google Chrome and Mozilla Firefox. Selenium Manager downloads the drivers. Playwright uses the installed Chrome, so
  it downloads nothing; for Playwright Firefox it downloads its own build into `tools/playwright/` (git-ignored).

## On a new computer (after `git clone`)
Nothing is machine-specific in git. Steps:
1. Install JDK 21+, Maven 3.9+, Chrome and Firefox.
2. Build once from the repo root, because `parabank-tests` needs `automation-core` in your local Maven repository:
   ```bash
   git clone https://github.com/goyalyogesh/ParabankAutomationFramework.git
   cd ParabankAutomationFramework
   mvn -q install -DskipTests
   ```
3. Run the tests (commands below). The first run creates `testdata/ParaBankData.xlsx` from the template and
   registers 10 new customers. Login and transfer suites reuse them, so on a fresh clone run the full suite (or the
   registration suite) first.
4. Optional: Jenkins. It is not in git (only its configuration is). Download `jenkins.war` and the plugins once into
   `tools/jenkins/` (guide 11, first block), then run `ci/jenkins/start-jenkins.sh`. It finds Maven on your PATH,
   creates its own admin password, and builds from your local clone.

Not needed: drivers (Selenium Manager downloads them), Playwright browsers (it uses your installed Chrome), any
password or account. JFrog is the only part that will need credentials, kept in `~/.m2/settings.xml`.
On Windows, the Maven commands work as they are; run the Jenkins start script from Git Bash or WSL.

## Build and unit tests
```bash
mvn -q install -DskipTests                     # build + install the core library (needed once)
mvn -pl automation-core test                   # core unit tests, no browser
mvn -pl automation-core test -Dgroups=browser -Dcore.excludedGroups=none   # same UI checks on Selenium and Playwright
mvn -pl parabank-tests test -DsuiteXmlFile=testng-unit.xml                 # ParaBank unit tests, no browser
```

## Run the ParaBank tests
```bash
mvn -pl parabank-tests test -Dheadless=true                          # all: registration → login → transfers → API
mvn -pl parabank-tests test -Dbrowser=firefox -Dthreads=2            # other browser / thread count
mvn -pl parabank-tests test -Dui.engine=playwright                   # same tests on Playwright instead of Selenium
mvn -pl parabank-tests test -DsuiteXmlFile=testng-registration.xml   # registration only
mvn -pl parabank-tests test -DsuiteXmlFile=testng-login.xml          # login + footer (needs registered users)
mvn -pl parabank-tests test -DsuiteXmlFile=testng-transfers.xml      # transfers only (needs registered users)
mvn -pl parabank-tests test -Dtest=ApiRunner                         # REST API suite only (needs registered users)
```
Results:
- `parabank-tests/target/reports/spark.html` (Extent);
- `target/cucumber/` (Cucumber HTML/JSON);
- `target/screenshots/` (failures);
- `testdata/ParaBankData.xlsx` (credentials and results written back; created from `ParaBankData-template.xlsx`).

**Expected today:** a full run executes 59 scenarios. All pass except those tagged `@knownDefect`:
- the 7 API defects (guide 12), which fail on purpose;
- B02 (UI Bill Pay), which is still under watch.

Known defects are reported separately and never count against the pass rate. ParaBank fixed the UI login and Bill
Pay defects by 2026-11-22. The tags on N01, N02, N07 and B01 were removed after 4 clean runs (D65).

## With the retry engine (the way CI runs it)
```bash
mvn -pl parabank-tests exec:java -Dheadless=true
# demo that reaches run 3 (Playwright): fail 6 logins on purpose in runs 1 and 2
mvn -pl parabank-tests exec:java -Dheadless=true -Dthreshold=100 \
  "-Dretry.demo.injectFailure=customer U0[1-6] can log in and out" -Dretry.demo.injectRuns=2
```

Options:
- `-Dthreshold=85` and `-Dretry.maxRuns=3`;
- `-Dbrowser.rotation=chrome,firefox,playwright:chrome`, where each entry is `[engine:]browser[-headless]`;
- `-Dthreads=3`.

Reports:
- `reports/run-N/`: Extent, Cucumber, screenshots and the Maven log for each run;
- `reports/combined/index.html`: every scenario × run, plus "passed only on retry" and "known defects that passed".

The exit code is non-zero if the threshold isn't met. Guides 10 and 13 explain the engine.

## In Jenkins (local)
`ci/jenkins/start-jenkins.sh` starts Jenkins at http://127.0.0.1:8081. Log in as `admin`; the password is in
`tools/jenkins/admin-password`. The job is **parabank-automation**.
- **Parameters:** `THREADS`, `THRESHOLD`, `BROWSER_ROTATION`, `HEADLESS`, `DEMO_RETRY`. `DEMO_RETRY` shows Chrome →
  Firefox → Playwright in one build.
- **Published:** JUnit results, the combined report and Extent; `reports/**` is archived.

Setup and design: `docs/guide/11-jenkins-pipeline.md`.

## Structure
```
automation-core/   reusable library → JFrog Artifactory (pending)
                   config · excel · data · driver · ui (UiDriver, Selenium, Playwright, UiSession) · api (ApiClient,
                   OpenApiContract) · report (Extent plugin, screenshots) · retry (orchestrator, combined report)
parabank-tests/    ParaBank pages, API client, models, services · features, steps, hooks, runners · testng-*.xml
testdata/          Excel template (tracked) and working workbook (git-ignored, results written back)
ci/jenkins/        Jenkins configuration as code + start script
Jenkinsfile        CI pipeline
docs/              guides, decisions, interview Q&A, build log, plans
reports/, tools/   generated reports, local Jenkins / Playwright browsers (git-ignored)
```

## Status
| Area | State |
|---|---|
| Registration, login, transfers, hybrid UI + API checks | Done |
| REST API suite with OpenAPI contract validation | Done (7 ParaBank API defects documented) |
| Retry engine, per-run and combined reports | Done |
| Playwright as run 3, by configuration | Done |
| Jenkins pipeline | Done |
| Publish `automation-core` to JFrog Artifactory | Pending (account to be created) |

Requirement-by-requirement status: [REQUIREMENTS.md](REQUIREMENTS.md), section 8.

## Documentation
- Requirements: [REQUIREMENTS.md](REQUIREMENTS.md)
- Guides, read in order:
  [01 Overview](docs/guide/01-project-overview.md) ·
  [02 Maven modules](docs/guide/02-maven-multi-module.md) ·
  [03 Config](docs/guide/03-config-reader.md) ·
  [04 Excel](docs/guide/04-excel-utility.md) ·
  [05 Driver & UI layer](docs/guide/05-driver-and-ui-layer.md) ·
  [06 Cucumber + TestNG](docs/guide/06-bdd-cucumber-testng.md) ·
  [07 Pages & registration](docs/guide/07-pages-data-and-registration.md) ·
  [08 Login & Selenium 4](docs/guide/08-login-known-defects-selenium4.md) ·
  [09 Transfers & hybrid](docs/guide/09-transfers-and-api-hybrid.md) ·
  [10 Retry engine](docs/guide/10-retry-engine-and-reports.md) ·
  [11 Jenkins](docs/guide/11-jenkins-pipeline.md) ·
  [12 API suite](docs/guide/12-api-suite-openapi.md) ·
  [13 Playwright](docs/guide/13-playwright-engine.md)
- Why things are the way they are: [docs/DECISIONS.md](docs/DECISIONS.md) (D1–D65)
- Interview preparation: [docs/INTERVIEW_QA.md](docs/INTERVIEW_QA.md), including "where each concept is used"
- What happened while building it: [docs/BUILD_LOG.md](docs/BUILD_LOG.md)
