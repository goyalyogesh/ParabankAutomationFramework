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

## Cucumber + TestNG and parallel execution

**Q: How do Cucumber scenarios run in parallel with TestNG?**
A: The runner extends `AbstractTestNGCucumberTests`; each scenario is a row of its `scenarios()` data provider.
Overriding it with `@DataProvider(parallel = true)` runs the rows on a pool whose size is `dataproviderthreadcount`
(we pass `-Dthreads`, default 3).

**Tweak: Login scenarios must only start after all registrations finished.**
A: Two `<test>` blocks in the suite XML run in order; parallelism happens only inside each block.

**Tweak: You ran `-Dcucumber.filter.tags=@registration` and registration ran twice. Why?**
A: That property overrides the tags of **every** runner in the JVM, so both runners picked the registration
scenarios. We use separate suite XML files instead.

**Q: How do step classes share data, and why not static fields?**
A: PicoContainer injects one `TestContext` per scenario into every step/hook constructor. Static fields are shared
by all threads, so parallel scenarios would overwrite each other's data.

**Q: In what order do `@After` hooks run?**
A: Highest `order` first. Screenshot (100) → Excel write-back (50) → quit browser (0). If the browser were quit
first, there would be nothing to screenshot.

**Tweak: The screenshot hook throws. Does the browser still close?**
A: Yes, Cucumber runs all after-hooks even if one fails; the failure is reported on the scenario.

## Test data, idempotency and the API

**Q: What does "idempotent registration" mean here?**
A: Running it twice gives the same end state. Before registering, the service checks the Excel credentials via
REST login; if they work, the user is marked `EXISTING` and nothing is created. Verified: second run → 10 × EXISTING.

**Q: Why write results back to Excel before the assertions?**
A: If a later check fails, the generated username/password would otherwise be lost and the user orphaned on the site.

**Q: Why call the REST API from a UI test?**
A: Setup and verification are faster and more reliable through the API, and here the API is the truth source:
when this was built, the UI accepted any credentials and the API did not.

**Q: A known defect started passing. What did you do?**
A: The retry engine reports "known defects that passed", so it was noticed on the first run. I checked the evidence
(Excel showed the real error message and the real debit), then kept the tag while watching a few more clean runs. After 4
clean runs I removed the tag from four cases. One case (B02) failed once with a different symptom, so it stays tagged.
Remove a tag too early and a flaky site turns the build red; keep it too long and it hides a regression.

**Tweak: Every registration failed with "This username already exists", even with unique names. How do you debug?**
A: Don't trust the message. Reproduce outside the framework (curl), change one variable at a time: random 14-char
name worked, our 21-char name didn't, 18 worked, 21 failed → hidden 20-char limit. Fix the generator and add the
finding to the docs.

**Tweak: Three parallel threads produced the same "unique" username.**
A: Each scenario created its own generator with its own counter starting at 1. The counter must be shared
(`static AtomicInteger`). We wrote a failing test first (300 instances → 1 distinct name), then fixed it.

## Known defects

**Q: The app has a bug that makes 3 tests fail. Why not change the assertions so they pass?**
A: Then the suite hides a real bug. We keep the correct assertion, tag the cases `@knownDefect`, show them in their
own report category, and exclude them from the pass-rate threshold and retries (Plan 04).

**Tweak: The bug gets fixed. What happens?**
A: Those tests start passing. Remove the tag (ideally linked to the bug ticket) so they count normally again.

## Selenium 4 and Collections

**Q: Where do you use relative locators, and what's the risk?**
A: Last Name = "input below First Name". Layout changes can break it, so the page reads the field's value back and
fails clearly if the wrong field was filled. For most fields, ids are still the better choice.

**Q: How do you handle a new tab?**
A: Remember the parent handle, `switchTo().newWindow(WindowType.TAB)`, check `getWindowHandles()` (a `Set`, since
handles are unique) has 2 entries, close the child, switch back to the parent.

**Q: Why `ConcurrentHashMap` for the user registry, not `HashMap` or `Collections.synchronizedMap`?**
A: Several threads write at once. `HashMap` can lose entries or corrupt; `synchronizedMap` locks the whole map for
every call; `ConcurrentHashMap` allows concurrent reads/writes with fine-grained locking. Our test registers 200
users from a parallel stream and checks all 200 are there.

**Tweak: You need the registered users in ID order for a report.**
A: Stream the values and sort: `values().stream().sorted(Comparator.comparing(ParaBankUser::userId)).toList()`.
A `ConcurrentHashMap` itself has no order.

## Transfers, hybrid checks and money

**Q: Why verify balances through the API when the test runs in the browser?**
A: The API gives exact values (`515.50` as `BigDecimal`) instead of formatted text, it's faster, and it shows what
the UI hides. Our UI Bill Pay said "Complete" while the API showed no debit at all, a real defect.

**Tweak: The UI says success but the backend didn't change. How do you prove whose bug it is?**
A: Isolate layers: check the API (no transaction → not timing); find the request the page sends (browser JS →
`services_proxy/bank/billpay`); replay it with curl (no debit); send the same body to the REST endpoint (debit
works). So the defect is in the proxy path, not in our test.

**Q: Why `BigDecimal` and `isEqualByComparingTo`?**
A: `double` can't store 0.1 exactly. `BigDecimal.equals` also compares scale (`15.0` ≠ `15`), so we compare values
with `compareTo` / `isEqualByComparingTo`.

**Q: Why an `ArrayDeque` for the transfer chain?**
A: Order matters (FIFO). `ArrayDeque` is O(1) at both ends, uses an array (no node per element like `LinkedList`) and
isn't synchronised (unlike `Stack`). One thread owns the queue, so no locking is needed.

**Tweak: The second transfer of the chain fails. What state are you in?**
A: The first one already happened (no rollback; it's a real bank call). The queue stops, `remaining()` holds the
transfers that never ran, and Excel marks them `NOT RUN`, so the report shows exactly what happened.

**Tweak: Several threads must take transfers from one shared queue.**
A: Switch to a `ConcurrentLinkedDeque` or a `BlockingQueue` (`LinkedBlockingDeque`) with `poll`/`take`, and keep
balance checks per account isolated.

**Q: How do you find the account that "Open New Account" created?**
A: Snapshot the account ids into a `Set` before, read them again after, and take the difference
(`after.removeAll(before)`); it must contain exactly the new number shown on screen.

**Q: How does the ledger compute expected balances?**
A: Streams: each transfer `flatMap`s into (sender, −amount) and (receiver, +amount), then
`groupingBy(account, TreeMap::new, reducing(ZERO, value, BigDecimal::add))`. Sanity check: all changes sum to 0.

**Q: Parallel tests checking balances, how do you avoid one test breaking another?**
A: Data partitioning: every parallel scenario owns its customers. API scenarios that reuse customers run in a
separate sequential block afterwards.

**Q: You found that `/transfer` accepts any two accounts without login. What do you do?**
A: Report it as a security defect (broken object-level authorisation, OWASP API #1), and don't hide it in the test.
Our chain scenario documents the behaviour; a dedicated negative test would assert a 401/403 once fixed.

## Flakiness from the environment

**Q: The public site started returning HTTP 429 halfway through the run. How did you handle it?**
A: Recognise it (Cloudflare 1015 page / status 429), wait (honour `Retry-After`, else 30/60/90 s), retry a limited
number of times, then fail with a clear message. API retries are unit-tested with the JDK's `HttpServer` and a fake
sleeper. Also: keep threads moderate (3), and don't hammer the site with manual probes during a run.

**Tweak: Is it safe to retry a POST that moves money?**
A: Only for responses that guarantee the server didn't process it, like 429 from the gateway. Never retry blindly on
timeouts or 500s for non-idempotent calls; check the state first (e.g. look for the transaction).

**Tweak: Why not just add `Thread.sleep(5000)` between tests?**
A: It slows every run and still fails when the limit is hit. Back-off only when the server says so.

## Retry engine and reports

**Q: How does your retry work?**
A: Run 1 executes everything on Chrome. The orchestrator reads Cucumber's JSON, computes the cumulative pass rate
without `@knownDefect` scenarios, and if it's below 85% re-runs only the scenarios that haven't passed yet on the
next browser (Firefox, then headless Chrome), at most 3 runs. Each run is a separate `mvn test` with its own reports;
a combined HTML page shows every scenario in every run.

**Tweak: Why not Surefire's `rerunFailingTestsCount` or TestNG's `IRetryAnalyzer`?**
A: Both retry immediately, in the same JVM and on the same browser, test by test. They can't apply a suite-level
threshold, switch browser or keep a report per attempt. They also hide flakiness. Ours shows "Passed on retry".

**Tweak: Why exclude known defects from the pass rate?**
A: They fail because of a real application bug, so retrying wastes time and the result is known. Counting them
would mix product bugs with automation health: 5 known defects out of 35 alone would push the rate to 85.7%.

**Q: How do you re-run just the failed Cucumber scenarios?**
A: Each scenario's id is `uri:line` from the JSON. We pass `-Dretry.scenarios=<ids>` and a runner base class filters
the TestNG data provider rows. We don't use `-Dcucumber.features=file:line` because a filter given as a property
replaces the runner's tags: all 4 runners re-ran all 6 scenarios (24 executions), which we saw live.

**Tweak: Your retry showed "PASSED". How do you know it really passed?**
A: Check the counts, not the verdict. Twice the verdict was green while the numbers were wrong: 24 executions
instead of 6, then 33 scenarios instead of 35 (a runner lost its `@DataProvider`, because annotations aren't
inherited by overriding methods). Both are now covered by tests.

**Q: How did you test the retry engine without a flaky site?**
A: Two ways. Unit tests with a fake `RunExecutor` that plays back prepared results per run (fast, deterministic).
And fault injection in the real suite: `-Dretry.demo.injectFailure=<regex>` fails matching scenarios in run 1 only,
so run 2 must retry exactly those on Firefox.

**Tweak: A scenario fails on Chrome and passes on Firefox. What does it tell you?**
A: Either a browser-specific bug (investigate, maybe a real defect) or flakiness (timing, data). The combined
report lists it as "Passed on retry", so it gets triaged rather than hidden.

**Tweak: Why a new Maven process per run?**
A: Clean state (static registries, ThreadLocal drivers, the report singleton), same as separate CI builds, and the
orchestrator only depends on files the run leaves behind: Cucumber JSON.

## Jenkins

**Q: Walk me through your pipeline.**
A: Declarative `Jenkinsfile`: Build (`mvn install -DskipTests`) → Unit tests (core + framework unit suite, JUnit
results published) → ParaBank suite through the retry engine with parameters (threads, threshold, browser rotation,
headless, retry demo) → post: archive `reports/**` and publish the combined report and the Extent report.
The build fails if the pass rate is below the threshold (the orchestrator exits non-zero).

**Q: How is Jenkins itself configured?**
A: As code: `ci/jenkins/jenkins.yaml` (JCasC) defines security, the admin user, PATH and the job (via Job DSL).
Starting from an empty `JENKINS_HOME` gives the same Jenkins every time, and every change is reviewed in git.

**Tweak: Where's the admin password?**
A: Generated on first start with `openssl rand`, saved with mode 600 in a git-ignored folder, passed to JCasC as an
environment variable. In a team: Jenkins credentials store or a secrets manager, never in the repo.

**Tweak: The Extent report is blank in Jenkins. Why?**
A: Jenkins serves archived HTML with a strict Content-Security-Policy (no scripts, no inline styles) to protect
users from malicious reports. We relaxed it only on this local, single-user Jenkins. On a shared server I'd keep
the default and download the report or use a report server.

**Tweak: Why one executor and no concurrent builds?**
A: Two builds at once double the traffic to a rate-limited site and share the same test customers in Excel.

**Tweak: Why the `retry.passThrough` property?**
A: The orchestrator starts a new Maven per run and only forwards known properties. A project-specific one
(`testdata.file`) had to reach the test JVM without hard-coding project names in the generic library.

**Q: A known defect suddenly passes in CI. What do you do?**
A: Our report lists it under "Known defects that passed". Verify it manually (here the Excel write-back showed a
real debit, 515.50 → 485.50), confirm across a couple of runs, then remove the tag so the scenario counts again.
Never auto-remove: a flaky pass would hide the defect.

## REST API suite

**Q: How do you test an API beyond status codes?**
A: Three layers: functional (exact balance changes with BigDecimal, follow-up queries find the transaction),
contract (every response validated against the OpenAPI spec), negative (bad input → 4xx with a clear message,
no side effects).

**Q: What is contract testing here?**
A: A Rest Assured filter (Atlassian swagger-request-validator) checks each request and response against the spec.
It found that `Transaction.date` is a number while the spec promises a date-time string. Consumers generated from
the spec would break on that.

**Tweak: Contract test vs. consumer-driven contract (Pact)?**
A: Ours checks the provider against its *published* spec. Pact checks the provider against what *consumers*
actually use. With one public API and no consumer teams, the spec is the contract.

**Tweak: The spec changes on the server tomorrow. What happens?**
A: Nothing until someone updates our local copy. That's deliberate: the update is a commit, reviewed, and
`ApiCoverageTest` immediately shows new or removed operations.

**Q: How do you know you covered the whole API?**
A: `ApiCoverageTest` reads all operations from the spec and requires each to be implemented by the client or
explicitly excluded with a reason. 22 implemented, 5 excluded admin operations.

**Q: Your negative test proves a withdrawal is wrongly accepted. Doesn't that damage test data?**
A: The step undoes it immediately (deposit back) before the assertion fails. Every money-changing scenario cleans
up; a run changes the main balance by only −$8.50, and I can explain every cent.

**Tweak: Why are some API scenarios known defects instead of failing the build?**
A: They document real bugs with the *correct* expected behaviour. They don't count toward the pass rate, are
never retried, and the report flags them if they ever pass (fixed). The build stays meaningful.

**Q: Name an API bug you found.**
A: Overdraft without limit ($100,000 from $275), negative withdrawals/transfers accepted, a loan approved with a
down payment bigger than the balance, a 500 HTML page for an invalid account type, a price-history endpoint
that never works, and a contract mismatch on dates.

## Playwright engine (Plan 07)

**Q: How did you add Playwright without rewriting your page objects?**
A: Pages only call a `UiDriver` interface with string locators like `id=username`. I wrote a second implementation,
`PlaywrightUiDriver`, and a `UiSession` that starts whichever engine the config names (`ui.engine`). It's the
Strategy pattern; the pages depend on an abstraction, not on Selenium (Dependency Inversion).

**Q: How do you know both engines behave the same?**
A: One abstract TestNG class, `UiDriverContractTest`, contains 13 checks against a local HTML page. A Selenium and a
Playwright subclass only start and stop their engine. Both must pass the same 13 checks.

**Q: What was different between the engines?**
A: Playwright locators are strict (two matches throw) so I use `.first()` like Selenium's `findElement`; `#a.b` in CSS
means id+class, so ids become `[id="a.b"]`; Playwright has pages, not window handles, so I give pages ids; there is
no relative locator, so `typeBelow` measures bounding boxes itself; waits are built in for actions, and I wrote a
small poll loop for "wait until text appears".

**Q: Why use Playwright only for run 3?**
A: By run 3 a scenario has already failed on Chrome and Firefox with Selenium. If it passes on Playwright, the problem
is likely timing or the engine, not the site. If it fails on all three, it's very likely a real defect.

**Q: Selenium or Playwright for a new project?**
A: Playwright for a modern web app with no Safari requirement: auto-waiting, fast isolated contexts, tracing. Selenium
when you need real Safari, a Grid or cloud vendor, or the team already knows it. With a `UiDriver` layer you can defer
the choice.

**Q: Is Playwright thread-safe?**
A: No. Each TestNG thread gets its own Playwright, browser, context and page through a `ThreadLocal` in `UiSession`.
They are closed in reverse order in `finally` blocks.
