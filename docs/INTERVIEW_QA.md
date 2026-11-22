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

**Q: How do you know your concurrency test actually tests something?**
A: I removed the lock and ran it: it failed 3 out of 3 times. A concurrency test that still passes without
the protection proves nothing.

## Test data generation

**Q: How do you make sure usernames never clash on a shared public site?**
A: `prefix_<yyyyMMddHHmmss>_<seq>`. The timestamp separates runs; an `AtomicInteger` sequence separates
threads inside one run. A test generates 500 names in a parallel stream and checks all are unique.

**Q: Why inject `Clock` and `Random` into the generator?**
A: So tests are deterministic: a fixed clock gives a known timestamp and a seeded `Random` gives repeatable
values. Production uses the system clock and `SecureRandom`.

**Tweak: Two Jenkins agents start a build in the same second.**
A: Add the Jenkins `BUILD_NUMBER` or hostname to the prefix (`qa_b123`), read through `ConfigReader`.

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
