# Build Log

A diary of what was built, in order, with the commands that were run and what actually happened.
Read it next to the guide (`docs/guide/`) to see how the project grew step by step.

Environment: macOS, Maven 3.10.0 running on JDK 27 (compiling for Java 21), Chrome + Firefox installed.

---

## Plan 01 — Foundation & core library (2026-11-21)

### Task 1 — Project skeleton
**What was built**
- `pom.xml` (parent, `packaging=pom`): lists the two modules, sets Java 21, pins all library and plugin
  versions in one place, and declares where `mvn deploy` publishes (JFrog Artifactory URL placeholder).
- `automation-core/pom.xml`: the reusable library. Builds the main JAR + sources JAR + Javadoc JAR.
  Browser tests are excluded by default (`core.excludedGroups=browser`).
- `parabank-tests/pom.xml`: empty test module that depends on `automation-core`.
- `.gitignore`, `log4j2-test.xml` (quiet logs for core tests), README, decisions log, guide chapters 01–02.

**Commands and results**
- `mvn -q -B clean package -DskipTests` → exit 0. Both module JARs built (empty for now, no code yet).

**Why it matters**
The modular split is the foundation for publishing `automation-core` to JFrog later: other projects add one
`<dependency>` and get all the utilities.

### Task 2 — ConfigReader
**What was built**
`core/config/ConfigReader.java`: reads settings with precedence: `-D` system property, then environment variable,
then `config.properties`. Typed getters (`getInt`, `getDouble`, `getBoolean`, `getList`) and `find()` returning
`Optional`. The environment map is injected, so tests can fake env vars.

**Commands and results**
- Test written first → `mvn -pl automation-core test -Dtest=ConfigReaderTest` failed to compile
  (`cannot find symbol ConfigReader`) — the expected "red" step.
- After adding the class → `Tests run: 7, Failures: 0, Errors: 0` — "green".

**Docs**: guide 03, first section of `INTERVIEW_QA.md` (configuration questions).

### Task 3 — ExcelUtil: reading sheets
**What was built**
`core/excel/ExcelUtil.java` with `readSheet` (sheet → `List<Map<String,String>>` keyed by header, built with
`IntStream` → `map` → `filter` → `toList()`) and `writeSheet` (create/replace one sheet, keep the others).
`DataFormatter` returns text exactly as Excel shows it (`90210`, not `90210.0`). Saves go to a `.tmp` file
first, then an atomic rename.

**Commands and results**
- Red: `cannot find symbol ExcelUtil`. Green: `Tests run: 5, Failures: 0`. No Log4j warning, because
  `log4j-core` is on the test classpath (decision D16).

### Task 4 — ExcelUtil: thread-safe write-back
**What was built**
`ExcelUtil.updateRow(file, sheet, keyColumn, keyValue, updates)`: finds the row (e.g. `UserId=U03`), writes the
values, appends unknown columns to the header. One `ReentrantLock` serialises the read-modify-write cycle.

**Commands and results**
- Red: `cannot find symbol: method updateRow(...)`. Green: `Tests run: 10, Failures: 0` (read + update tests).
- **Experiment:** lock removed → `concurrentUpdatesAreAllPersisted` failed 3/3 runs
  (`UncheckedIOException: Cannot update Excel file`). Lock restored → all green. Proof the test is meaningful.

**Docs**: guide 04 (with the experiment), Excel section of `INTERVIEW_QA.md`.

### Task 5 — TestDataGenerator
**What was built**
`core/data/TestDataGenerator.java`: `uniqueUsername("qa")` → `qa_20261121183405_007` (timestamp separates
runs, `AtomicInteger` sequence separates threads), `password()` (3 upper + 4 lower + 3 digits, no look-alike
characters), `ssn()`, `phone()`, `zipCode()`. `Clock` and `Random` are injected so tests are repeatable.

**Commands and results**
- Red: `cannot find symbol TestDataGenerator`. Green: `Tests run: 4, Failures: 0`, including 500 usernames
  generated in a parallel stream with the same frozen clock, all unique.

### Task 6 — Locator
**What was built**
`core/ui/Locator.java` (a Java `record`): parses `strategy=value` strings such as `id=customer.firstName`,
`name=username`, `css=#rightPanel h1`, `xpath=//input[@value='Log In']`, `link=Log Out`. Only the first `=`
splits, so XPath values with `=` work. Clear errors for missing/unknown strategy or empty value.
It has no Selenium import on purpose: the Playwright engine (Plan 07) will reuse it.

**Commands and results**
- Red: `cannot find symbol Locator`. Green: `Tests run: 7, Failures: 0`.

### Task 7 — BrowserType, DriverFactory, DriverManager
**What was built**
- `BrowserType` enum (`CHROME`, `FIREFOX`) with a forgiving `from(" Chrome ")` and a clear error for `ie`.
- `DriverFactory.create(type, headless)`: Chrome `--headless=new`, Firefox `-headless`, 1920×1080, password
  pop-ups disabled, 60 s page-load timeout. Drivers come from Selenium Manager (no WebDriverManager).
- `DriverManager`: `ThreadLocal<WebDriver>` with `start / get / isStarted / quit` (quit always calls `remove()`).

**Commands and results**
- Red: `cannot find symbol BrowserType`. Green: `DriverManagerTest` 4/4 (no browser needed).
- Real browsers: `mvn -pl automation-core test -Dtest=DriverSmokeTest -Dgroups=browser -Dcore.excludedGroups=none`
  → headless Chrome **and** Firefox opened a local page and read its title: 2/2 passed in ~16 s.
- Default `mvn -pl automation-core test` → 32 tests, browser group correctly excluded.

### Task 8 — UiDriver and SeleniumUiDriver
**What was built**
- `UiDriver` interface: `open, click, type, text, isVisible, selectByVisibleText, selectedOption, options,
  waitForText, title, currentUrl, screenshot`. Page objects (Plan 02) will only see this interface.
- `SeleniumUiDriver`: every action uses an explicit `WebDriverWait` condition (clickable / visible / "select
  has options"). No implicit waits, no `Thread.sleep`. `toBy(Locator)` maps our locator strings to Selenium `By`.

**Commands and results**
- Red: `cannot find symbol SeleniumUiDriver`. Green: mapping test 1/1.
- Real browser (headless Chrome) on a local test page whose dropdown is filled by JavaScript after 0.7 s:
  5/5 passed — typing clears old text, waits for late options, `isVisible` never throws, screenshot is a PNG.

### Plan 01 — final verification
| Check | Result |
|---|---|
| `mvn -pl automation-core test -Dcore.excludedGroups=none` (unit + real browsers) | **40 tests, 0 failures** |
| `mvn -pl automation-core test` (default, no browser) | 33 tests, 0 failures (32 after Task 7 + the mapping test from Task 8; corrected in Plan 02) |
| `mvn clean install -DskipTests` | `automation-core-1.0.0-SNAPSHOT.jar`, `-sources.jar`, `-javadoc.jar`, `.pom` installed in `~/.m2` |
| Commits | 8, all authored by Yogesh Goyal |

**Next:** Plan 02 (registration & login with Cucumber + TestNG, Excel write-back, Collections, Selenium 4 features).

---

## Plan 02 — Registration & login (2026-11-21)

### Task 1 — Dependency versions
Parent POM pins Cucumber **7.34.9** (newest 7.x; 8.1.0 was released 6 days ago, too new), Rest Assured **6.2.0**
(brings Groovy 5, which supports the JDK 27 that Maven runs on here) and ExtentReports **5.1.2**.
`automation-core` declares them as `<optional>` so projects that don't use the Cucumber/Extent/API helpers don't inherit them.
Result: core build green, 33 tests.

### Task 2 — Selenium 4 actions in UiDriver
Added `value, attribute, waitVisible, waitForAnyVisible, typeBelow (relative locator), openInNewTab (WindowType.TAB),
currentWindow, windowHandles (Set), switchToWindow, closeCurrentWindow`.
- Red (confirmed by temporarily removing the new code): `cannot find symbol: method typeBelow`.
- Green: `SeleniumUiDriverBrowserTest` 9/9 in headless Chrome (relative locator typed into the field below,
  new tab opened, 2 distinct handles, tab closed, parent restored).

### Task 3 — ScreenshotUtil and ExtentCucumberPlugin
- `ScreenshotUtil.save(png, dir, name)` → `<name>_t<threadId>_<nanoTime>.png`, so parallel threads never overwrite files.
- `ExtentCucumberPlugin` (a Cucumber `ConcurrentEventListener`): one Extent test per scenario, one line per step,
  Base64 screenshots from `scenario.attach`, text from `scenario.log`, category "Known defect" for failed
  `@knownDefect` scenarios. Runners in the same JVM that use the same path share one report
  (`ConcurrentHashMap<Path, ExtentReports>`).
- Tested by running a real mini Cucumber suite (`report-fixture/demo.feature`) through `io.cucumber.core.cli.Main`
  and reading the produced HTML. Red: `cannot find symbol ExtentCucumberPlugin`. Green: 2/2.

### Task 4 — ApiClient
`core/api/ApiClient`: one shared Rest Assured `RequestSpecification` (base URI + `Accept: application/json`);
`request()` logs request/response only when a validation fails. Red → green (1/1). Core installed to `~/.m2`
with 36 default tests passing (Rest Assured 6 / Groovy 5 loads fine on JDK 27).

### Task 5 — parabank-tests setup, config, model
- `parabank-tests/pom.xml`: Cucumber (TestNG runner + PicoContainer), Rest Assured, Extent, Log4j2; Surefire picks the
  suite with `-DsuiteXmlFile` (default `testng-parabank.xml`) and the thread count with `-Dthreads` (default 3).
- `ParaBankConfig` (typed settings), records `ParaBankUser`, `LoginCase`, `RegistrationResult`, enum `RegistrationStatus`.
- Red: `package ...parabank.model does not exist`. Green: `ParaBankUserTest` 3/3.
- **Gotcha found:** Maven printed "dependency version is missing" because the *parent* POM in `~/.m2` was the old one
  (`mvn -pl automation-core install` does not reinstall the parent). Fix: `mvn install -N` (or build with `-am`).

### Task 6 — Excel repository, user registry, workbook
- `TestDataRepository`: Users / LoginNegative sheets ↔ records; `{U01}` placeholders resolve to U01's username;
  `recordRegistration`, `recordRegistrationFailure`, `recordLoginResult` write back through `ExcelUtil.updateRow`.
- `UserRegistry`: static `ConcurrentHashMap<String, ParaBankUser>`; test registers 200 users from a parallel stream.
- Red: `package ...parabank.data does not exist`. Green: unit suite 11/11.
- `CreateTestDataWorkbook` tool generated `testdata/ParaBankData.xlsx` (sheets Users: 10 customers U01–U10 with blank
  credentials; LoginNegative: N01–N07).

### Task 7 — REST client, page objects, registration service
- `ParaBankApi`: `login(u, p)` → customer id or empty (the API rejects bad credentials, unlike the UI);
  `accountIds(customerId)`.
- Pages: `LoginPage` (outcome LOGGED_IN / ERROR_SHOWN / NO_PARABANK_RESPONSE), `RegisterPage` (Last Name filled with the
  Selenium 4 relative locator and self-checked), `AccountsOverviewPage`, `FooterComponent`.
- `RegistrationService`: API check for existing credentials → generate if blank → UI register → retry once with a new
  name if taken → customer id and first account via API.
- Compiles; exercised live in Task 8.

### Task 8 — Cucumber wiring and registration (first live run: what went wrong and what we fixed)
Built `TestContext` (PicoContainer, one per scenario), `Hooks` (browser per scenario, failure screenshot, Excel
write-back of failures, quit), `RegistrationSteps`, `RegistrationRunner` / `LoginRunner` (parallel `@DataProvider`),
`testng-parabank.xml`, `registration.feature` (Scenario Outline U01–U10).

**First live run** (`-Dcucumber.filter.tags=@registration`): 20 failures, all "This username already exists."
Three separate findings:

1. **Real bug in our generator.** The log showed U01, U02 and U03 all trying `qa_20261121193807_001`. Each scenario
   creates its own `TestDataGenerator`, and the sequence counter was an *instance* field, so every instance started at
   001 and three threads in the same second collided. Fix: the counter is now `static` (one per JVM).
   Regression test `separateInstancesNeverProduceTheSameUsername` failed first (300 instances → **1** distinct name),
   then passed after the fix. Committed separately as a `fix(core)` commit.
2. **20 runs instead of 10.** `-Dcucumber.filter.tags` replaces the tags of *every* runner, so LoginRunner also ran
   the registration scenarios. Fix: dedicated suites `testng-registration.xml` and `testng-login.xml`; the full flow
   stays in `testng-parabank.xml`.
3. **Cloudflare rate limit.** The burst (20 browsers + manual curl probes) got this machine HTTP 429
   "error code: 1015". New `SiteGuard` makes `LoginPage.open()` / `RegisterPage.open()` fail fast with
   "ParaBank is rate-limiting this machine … lower -Dthreads" instead of a vague timeout. Default threads stay at 3.

Parallelism itself worked: the log shows `TestNG-PoolService-1/2/3` threads (3 threads, as configured).

**Second live run** (after the rate limit cleared): still 10/10 "This username already exists", even with unique names.
Probing with curl showed the real rule: **ParaBank rejects any username longer than 20 characters, with the misleading
message "This username already exists."** (18 chars → registered, 21 chars → "already exists"). Our format
`qa_yyyyMMddHHmmss_NNN` is 21 chars. Fix: 2-digit year, `qa_yyMMddHHmmss_NNN` = 19 chars. Lesson for interviews:
don't trust an error message, isolate one variable at a time (here: length vs. characters).

**Third run — green.** `testng-registration.xml`, headless Chrome, 3 threads: 10/10 registered in 13 s
(`qa_261121194737_001` … `_010`, customer ids 20870–21980). The Users sheet got Username, Password, Status, Acknowledgement,
CustomerId, AccountNumber, RegisteredAt, Browser. **Second run:** 10/10 `EXISTING` (REST login succeeded), 0 registered again
→ registration is idempotent.

### Task 9 — Login, negative cases, Selenium 4 new tab (full suite, both browsers)
`mvn -pl parabank-tests test -Dheadless=true` (Chrome) and `... -Dbrowser=firefox`:

| Browser | Scenarios | Passed | Failed | Failed scenarios |
|---|---|---|---|---|
| Chrome (headless) | 28 | 25 | 3 | N01, N02, N07 — all `@knownDefect` |
| Firefox (headless) | 28 | 25 | 3 | same |

- **Finding:** N07 (300-character username and password) was planned as an "input validation" case because during the
  spike the site showed an error for it. In the real run it **logged in**. Checked by hand with curl: the first try
  logged in, the second showed "An internal error has occurred". So it is the same defect as N01/N02 (the UI accepts any
  credentials) and moved to the `@knownDefect` examples.
- LoginNegative sheet after the run: N01/N02/N07 `FAIL LOGGED_IN`; N03–N05 `PASS ERROR_SHOWN: Please enter a username
  and password.`; N06 `PASS NO_PARABANK_RESPONSE` (Cloudflare blocks the SQL-like input before ParaBank sees it).
- `target/reports/spark.html`: one report for both runners, 3 Base64 screenshots, category "Known defect".
- `target/rerun/login.txt`: `classpath:features/login.feature:35:36:37` — input for the Plan 04 retry engine.
- Maven reports BUILD FAILURE because of the 3 known defects; Plan 04's threshold logic excludes them.
- `testdata/ParaBankData.xlsx` in git stays the blank template (no credentials); each machine fills its own copy.

---

## Plan 03 — Transfers + Rest Assured hybrid (2026-11-21)

### Task 1 — Actions API and text helpers
`UiDriver` + `SeleniumUiDriver`: `hover` (Actions `moveToElement`), `scrollTo` (Selenium 4 Actions `scrollToElement`),
`texts` (all matches, trimmed), `waitForNonBlankText` (values filled in by JavaScript, e.g. a new account number).
Red (implementation stashed): `cannot find symbol: method hover`. Green: browser test 13/13.

### Task 2 — ExcelUtil sheet names and headers
`sheetNames(file)` and `readHeaders(file, sheet)` (needed to upgrade an older working workbook with new sheets).
Red: `cannot find symbol: method sheetNames`. Green 2/2; core default suite 39/39, installed.

### Task 3 — Money models, ledger, ArrayDeque queue
Records `Account`, `Transaction`, `TransferCase`, `TransferInstruction` (money = `BigDecimal`), enum `TransferType`.
`TransferQueue` (ArrayDeque, FIFO; failure stops the queue, the rest stays in `remaining()`),
`TransferLedger.expectedNetChange` (Streams `flatMap` + `groupingBy(TreeMap)` + `reducing(BigDecimal::add)`).
Red: `cannot find symbol: class TransferInstruction`. Green: unit suite 14/14 (the ledger test also checks that all
net changes add up to 0 — money is moved, never created).

### Task 4 — Transfers sheet, template workbook, API money calls
- `testdata/ParaBankData.xlsx` → renamed **`ParaBankData-template.xlsx`** (tracked, never written). Tests use a
  git-ignored working copy `testdata/ParaBankData.xlsx`, created from the template on first run
  (`TestDataRepository.fromTemplate`). An older working copy gets new sheets added and keeps its credentials.
  Why: before this, every test run modified a tracked file, and resetting it threw away the registered credentials.
- `CreateTestDataWorkbook` now only adds missing sheets (output: "Keeping existing sheet Users", … `[Users, LoginNegative, Transfers]`).
- Transfers rows: T01–T03 OWN (U01–U03), B01 U04→U05, B02 U06→U07 BILLPAY, C01–C03 CHAIN U08→U09→U10→U08.
- `ParaBankApi`: `accounts`, `account`, `balance`, `transactions`, `transfer`; JSON numbers parsed as `BigDecimal`.
- `TestContext.registeredUser(id)` shared by login and transfer steps; browser hook skips `@api` scenarios.
- Unit suite 17/17.

### Task 5 — Page objects
`AccountServicesMenu` (hover → click → wait for the target page title; enum `Link`), `OpenAccountPage`,
`TransferFundsPage`, `BillPayPage`, `AccountActivityPage`; `AccountsOverviewPage.accountIds/openActivity` (scroll).

### Task 6 — Transfer scenarios and live runs (what went wrong and what we changed)
**Run 1 (Chrome):** registration 10/10 (new users from the fresh working copy), 8 logins passed, then
**Cloudflare blocked the machine** (Error 1015 / HTTP 429): 15 failures, including every transfer. Two lessons:
the browser's block page says "Error 1015 – You are being rate limited" (our check only knew curl's
"error code: 1015"), and the suite now sends enough requests to hit the limit.
→ `ApiClient.send` retries 429 (honours `Retry-After`, else 30/60/90 s; 4 unit tests with the JDK `HttpServer`
and a fake sleeper); `SiteGuard` recognises both block pages, waits and reloads; every menu navigation is guarded.

**Run 2 (Chrome):** 29/34 passed. T01–T03 and the chain passed. B01/B02 failed: payer balance unchanged
(expected 485.50, was 515.50). Investigation (see guide 09): the UI's `services_proxy/bank/billpay` confirms but
never debits, while the REST `services/bank/billpay` works → **new ParaBank defect**. B01/B02 → `@knownDefect`;
added **B03** (A → B Bill Pay through REST) so the requirement is covered by a working channel.
API scenarios moved to their own sequential block (`ApiRunner`, `@api`) after the parallel browser block, so B03 can
reuse U04/U05 safely. Gson declared explicitly (needed to send the payee as JSON). The working copy picks up the new
B03 row (template rows merged by key; unit test extended first: failed, then passed).

**Run 3 (Chrome):** 35 scenarios, **30 passed, 5 failed = exactly the known defects** (N01, N02, N07, B01, B02).
One 429 during the API block with `Retry-After: 300` → client waited 5 minutes → passed.

**Run 4 (Firefox):** 35 scenarios, 30 passed, same 5 known defects, no rate limit.

Transfers sheet after the Firefox run (excerpt):

| Id | Result | From before → after | To before → after | Channel |
|---|---|---|---|---|
| T01 | PASS | 195.50 → 170.50 | 90.00 → 115.00 | FIREFOX |
| T02 | PASS | 164.50 → 124.00 | 90.00 → 130.50 | FIREFOX |
| B01 | FAIL | 507.25 → 507.25 | 515.50 → 515.50 | FIREFOX (defect: no debit) |
| B03 | PASS | 507.25 → 500.00 | 515.50 → 515.50 | REST API |
| C01–C03 | PASS | net changes as expected | | REST API |

Report: 5 Base64 screenshots in category "Known defect"; rerun files list exactly those 5 scenarios.

## Plan 04 — Retry engine and combined report
- Core `core.retry`: JSON reader, cumulative history, threshold policy, rotation, orchestrator, Maven executor,
  combined report, `RetrySelection` + `RetryAwareCucumberTests`. Core tests 58, parabank unit tests 18.
- Live normal run: 35 executed, 30 passed, 100% (5 known defects excluded), no retry, BUILD SUCCESS.
- Live demo, try 1 (`cucumber.features=file:line`): run 2 executed 24 instead of 6; report crashed on duplicates.
- Try 2 (`cucumber.filter.name`): same 24; logins rate-limited; misleading green.
- Try 3 (data-provider filter): run 2 = 6, only in login runner, but run 1 = 33 (ApiRunner lost `@DataProvider`).
- Final: run 1 = 35 executed, 23 passed (76.7%); run 2 on Firefox = exactly 7 (6 injected + T01, a real Chrome
  failure), all passed → 100%, PASSED. T01 shows as "Passed on retry" in the combined report.

## Plan 05 — Jenkins (JFrog later, by the user's choice)
- Jenkins 2.580.2 LTS WAR (SHA-256 verified) + 71 plugins in `tools/jenkins/` (git-ignored). Configured as code
  (`ci/jenkins/jenkins.yaml`), started by `ci/jenkins/start-jenkins.sh` on 127.0.0.1:8081. Anonymous → 403.
- `retry.passThrough` added (TDD) so the CI workbook path reaches each run. Core tests 59 → 60 with the next item.
- **Build 1** (normal): Build ✓, unit tests 77/77 published, suite run 1 = 35 executed, 3 failures from a
  Cloudflare rate limit (SiteGuard waits weren't enough) → 90% ≥ 85% → SUCCESS, no retry. Reports published (200).
- **Surprise:** 3 known defects passed (N01, B01, B02). Excel write-back proved they were real: B01 debited
  515.50 → 485.50, N01 showed the error. ParaBank had apparently been reset/changed overnight.
  → New core feature (TDD): "Known defects that passed" in console and combined report.
- **Build 2** (`DEMO_RETRY=true`): run 1 = 35 in ~1 minute, 6 injected failures → 80% → run 2 on Firefox =
  exactly 6, all passed → 100%, SUCCESS. **All 5 known defects passed** (no rate limit this time).
- Note: unit tests of the retry engine print `[retry]` lines too, so the Jenkins console shows a few fake
  "Run 1 done" lines from the Unit tests stage (timestamps of that stage). Real ones follow in the suite stage.
