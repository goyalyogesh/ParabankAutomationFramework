# ParaBank Automation Framework — Design Spec

Date: 2026-11-21 · Status: **Approved requirements** · Owner: Yogesh

## 1. Purpose

A showcase test automation framework (Java + Selenium + Cucumber + TestNG + Rest Assured) for the public demo bank
<https://parabank.parasoft.com/parabank>. It must be explainable end-to-end by its owner, so every design decision is
recorded in `docs/DECISIONS.md` and every component has a plain-language chapter in `docs/guide/`.

## 2. Functional requirements

| # | Requirement |
|---|---|
| F1 | Read test data from one Excel workbook (`testdata/ParaBankData.xlsx`), sheets: `Users`, `LoginNegative`, `Transfers`. |
| F2 | Register 10 users from the `Users` sheet. Blank username/password → auto-generate unique values (`qa_<yyyyMMddHHmmss>_<nn>`). |
| F3 | Registration is idempotent: if a user from Excel can still log in (checked via API), skip; otherwise (re)register. The demo DB is shared and gets reset. |
| F4 | After registration write back to Excel: `Status`, `Acknowledgement` (site message), `CustomerId`, `AccountNumber`, `RegisteredAt`, generated credentials. |
| F5 | Login positive: each registered user logs in and lands on *Accounts Overview*. |
| F6 | Login negative: wrong password, unknown user, empty fields, special-char/SQL-like input. Wrong-password and unknown-user are tagged `@knownDefect` (live UI accepts any credentials — verified 2026-11-21; API rejects correctly). |
| F7 | Transfer between own accounts: open a 2nd account, Transfer Funds, verify UI message and balances. |
| F8 | Cross-user payment: user A pays user B's account via Bill Pay; verify A's debit and B's credit. |
| F9 | Hybrid verification: after F7/F8, Rest Assured reads `/accounts/{id}` to confirm balances from the backend. |
| F10 | Phase 2: full Rest Assured API suite from `services/bank/openapi.yaml`. |
| F11 | Java Collections used where they solve real problems: `ConcurrentHashMap<String, User>` registry of registered users shared across parallel threads (pairs user A with user B for Bill Pay); `ArrayDeque` of transfers from the `Transfers` sheet processed in order with `pollFirst()`; `Set<String>` of account numbers proving uniqueness; Streams to filter/sort transactions and aggregate run results. |
| F12 | Selenium 4 features: relative locator on Register (`with(By.tagName("input")).below(By.id("customer.firstName"))` finds Last Name); Actions API `scrollToElement`/`moveToElement` on Accounts Overview rows; footer link opened via `WindowType.TAB`, title checked, handles collected in a `Set`, tab closed and parent restored. |

## 3. Execution requirements

| # | Requirement |
|---|---|
| E1 | Browsers: Chrome (primary), Firefox (secondary). Visible locally, headless in Jenkins; override `-Dheadless=true`. |
| E2 | Parallel scenario execution via TestNG DataProvider, threads configurable (`-Dthreads`), default 3. |
| E3 | Retry engine: Run 1 all tests on Chrome. Compute cumulative pass % (excluding `@knownDefect`). If < 85% (`-Dthreshold`), Run 2 = only failed scenarios on Firefox. If still < 85%, Run 3 = remaining failures on headless Chrome. Then stop. Browser order configurable (`-Dbrowser.rotation=chrome,firefox,chrome-headless`). |
| E4 | Phase 2: Run 3 switches to Playwright (`PlaywrightUiDriver`) by config only. |
| E5 | Reports (Extent in offline mode, Base64 screenshots embedded + PNG files named by thread id and timestamp so parallel runs never overwrite): Extent Spark + Cucumber HTML per run (`reports/run-1`, `run-2`, `run-3`) + combined report (`reports/combined`) showing per-scenario history across runs, screenshots on failure, known defects section. |
| E6 | Jenkins (local, Homebrew) pipeline via `Jenkinsfile`; parameters: threads, threshold, headless; archives & publishes reports. |
| E8 | Logging with Log4j2; Selenium's SLF4J logs routed through `log4j-slf4j2-impl`. The core library depends on `log4j-api` only; the test module provides the implementation. |
| E9 | Suites are TestNG XML files at module root, chosen with `mvn test -DsuiteXmlFile=...` (Surefire forced to the TestNG provider). |
| E7 | `automation-core` published as a versioned Maven artifact to JFrog Artifactory (free cloud). Credentials only in `~/.m2/settings.xml` / Jenkins credentials, never in git. |

## 4. Architecture

```
Banking_Automation_Framework/            (git, branch master)
├── pom.xml                              parent: versions, plugins, modules
├── automation-core/                     reusable library → JFrog Artifactory
│   └── io.github.thegoyalyogesh.qa.core
│       ├── config      ConfigReader (properties < env < -D)
│       ├── excel       ExcelUtil (POI, streams, thread-safe write-back)
│       ├── data        TestDataGenerator
│       ├── driver      BrowserType, DriverFactory, DriverManager (ThreadLocal)
│       ├── ui          Locator, UiDriver (interface), SeleniumUiDriver
│       ├── api         ApiClient (Rest Assured base)            [Plan 3]
│       └── retry       RunResult, ThresholdPolicy, RetryOrchestrator [Plan 4]
├── parabank-tests/                      the project under test
│   ├── src/main/java/.../parabank
│   │   ├── pages       page objects (use UiDriver only)
│   │   ├── api         ParaBankApi (Rest Assured)
│   │   └── model       User, Account records
│   └── src/test
│       ├── java/.../steps, hooks, runners
│       └── resources/features/*.feature, config.properties
├── testdata/ParaBankData.xlsx
├── Jenkinsfile
└── docs/  README, DECISIONS, INTERVIEW_QA, guide/
```

Key design rules:
- Page objects talk to `UiDriver`, never to Selenium directly → engine swap (Playwright) without rewriting pages.
- Locators are strings (`id=…`, `name=…`, `css=…`, `xpath=…`).
- Thread safety: one driver per thread (`ThreadLocal`), Excel writes guarded by a lock.
- Java Collections + Stream API used deliberately (Excel → `List<Map<String,String>>`, result aggregation, filtering failures).

## 5. Verified site facts (2026-11-21)

- Login form: `name=username`, `name=password`, button `Log In`. Empty → "Please enter a username and password."
- Register: ids `customer.firstName`, `customer.lastName`, `customer.address.street|city|state|zipCode`, `customer.phoneNumber`, `customer.ssn`, `customer.username`, `customer.password`, `repeatedPassword`.
- Open account: `id=type`, `id=fromAccountId`, button `Open New Account`, result `id=openAccountResult`.
- Transfer: `id=fromAccountId`, `id=toAccountId`, result `id=showResult`, error `id=showError`.
- Bill Pay: `name=payee.name|payee.address.street|…|payee.accountNumber`, `name=verifyAccount`, `name=amount`, `name=fromAccountId`, button `Send Payment`, result `id=billpayResult`.
- REST base: `/parabank/services/bank`, JSON needs `Accept: application/json`. `GET /login/{u}/{p}` returns customer or "Invalid username and/or password".

## 6. Lessons reused from SauceDemoExercise (2026-11-21)
Versions proven on this Mac (Selenium 4.50.0, TestNG 7.12.0, Surefire 3.5.6, compiler 3.15.0, Log4j 2.26.1, Extent 5.1.2);
ThreadLocal `ExtentTest`; Base64 screenshots; Jenkins: Extent needs the Jenkins CSP relaxed
(`System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "")` in Script Console), `junit` + `archiveArtifacts` +
`publishHTML(allowMissing)`, build timeout, `.jenkins-local/` git-ignored.

## 7. Out of scope (for now)
IE (retired, impossible on macOS), Safari, mobile, performance testing.

## 8. Delivery status (2026-11-22)

| # | Status | Where |
|---|---|---|
| F1–F5 | Done | guides 04, 07; `RegistrationService`, `TestDataRepository` |
| F6 | Done. The UI defect was fixed by ParaBank on 2026-11-22, so the `@knownDefect` tag was removed after 4 clean runs (D65) | guide 08 |
| F7, F9 | Done | guide 09 |
| F8 | Done, with a finding: Bill Pay treats the payee as an external company, so B's balance does not change (checked through the API). UI defect fixed for B01; B02 still watched | guide 09 |
| F10 | Done: 22 operations, OpenAPI contract validation, 26 scenarios, 7 API defects found | guide 12 |
| F11, F12 | Done | guides 07, 08 |
| E1, E2, E5, E8, E9 | Done | guides 05, 06, 10 |
| E3 | Done; superseded by E4 for run 3. Default rotation is now `chrome,firefox,playwright:chrome` | guide 10 |
| E4 | Done: `PlaywrightUiDriver`, `ui.engine` / `playwright:` rotation entries; shown live in Jenkins builds 4 and 6 | guide 13 |
| E6 | Done, plus parameter validation against command injection (D64) | guide 11 |
| E7 | **Open**: the owner will create the JFrog account; `distributionManagement` is ready in the root POM | plan 05, task 6 |
