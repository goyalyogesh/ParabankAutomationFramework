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

**Tweak: Run on Selenium Grid / cloud (BrowserStack, Sauce Labs) instead of local browsers.**
A: Done with Sauce Labs (guide 15): a `RemoteWebDriver` to the Sauce endpoint, created in one class (`SauceLabs`) and
kept in the same `ThreadLocal`. Pages and steps did not change. Selected with `-Dui.engine=sauce -Dbrowser=safari`.

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

**Q: Tell me about a UI bug you found that the API tests could not see.**
A: Find Transactions showed every transaction one day early. The API stores the booking date as midnight UTC; the
page formats it with the browser's time zone, so in Toronto (UTC−5) midnight UTC becomes the previous evening. I
caught it because the test compares the date on screen with the date from the API, not with my own clock. It only
appears for users west of UTC, so a team testing from Europe would never see it. Same page: `02-30-2026` is accepted
and silently returns "no transactions", because the browser only checks the format and the API doesn't validate the
date. Both are tagged `@knownDefect` and reported, not hidden.

**Tweak: How do you make date tests stable around midnight?**
A: Take the expected date from the system under test (the API's booking date), never from `LocalDate.now()`, and
search a range around it. Time zones are explicit in the code (`ZoneOffset.UTC`), never the machine default.

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
next browser (Firefox, then Playwright on Chrome), at most 3 runs. Each run is a separate `mvn test` with its own reports;
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

## Where each concept is used in this project (concrete answers)

Use these when an interviewer asks "where did you use X?". Each answer names the class, says why that choice, and
what the alternative would have cost. Paths are shortened: `core/` = `automation-core/src/main/java/.../qa/core/`,
`pb/` = `parabank-tests/src/{main,test}/java/.../qa/parabank/`.

### Collections

**Q: Where did you use `ConcurrentHashMap`, and why not `HashMap`?**
A: Two places:
1. `pb/data/UserRegistry.java:17`, a `ConcurrentHashMap<String, ParaBankUser>` keyed by user id (U01…U10). Registration
   scenarios run on 3 TestNG threads at once (`@DataProvider(parallel = true)`), and each thread calls
   `UserRegistry.register(...)` (`RegistrationSteps.java:41`) with the freshly created username and password. Later,
   `TestContext.registeredUser()` (`TestContext.java:78`) reads it back with `UserRegistry.find(id)` and falls back to
   Excel only if the user isn't in memory. So login and transfer scenarios in the same run use the new credentials
   without re-reading the workbook.
2. `core/report/ExtentCucumberPlugin.java:39,42`, `ConcurrentHashMap<UUID, ExtentTest>`. Cucumber events from parallel
   scenarios arrive on different threads, and each event must find its own report node by the test case id.
   `REPORTS.computeIfAbsent(path, ...)` (line 46) creates the shared `ExtentReports` exactly once even if two runners
   start at the same moment.

Why: a plain `HashMap` written by 3 threads can lose entries or corrupt its internal table. `Collections.synchronizedMap`
works but locks the whole map for every call. `ConcurrentHashMap` locks per bin, never throws
`ConcurrentModificationException` while iterating, and has atomic `computeIfAbsent`/`putIfAbsent`.
Proof: `UserRegistryTest` registers 200 users from a `parallel()` stream and expects exactly 200.

**Q: Where did you use a `Deque` / `ArrayDeque`?**
A: `pb/service/TransferQueue.java:27`. The `CHAIN` transfer scenario reads several transfers from the Transfers sheet.
They must run **in order**, because each one moves money the next one may need. They're added with `offerLast` and
processed with `pollFirst()` (line 46) until the queue is empty (FIFO). `ArrayDeque` gives O(1) at both ends and has
no locking overhead (one scenario thread owns the queue). Not `LinkedList`: more memory per element and worse cache
locality. Not `Stack`: synchronised and LIFO.

**Q: Where did you use a `Set`, and why?**
A: Wherever uniqueness or "what changed" matters:
- `TransferSteps.java:88,96`: account ids before and after "Open New Account" go into two `HashSet<Long>`s. The
  difference must be exactly one id, which proves a new account was created and identifies it.
- `ApiSteps.java:172-178`: the same for the API; `ApiSteps.java:234` for share positions.
- `UiDriver.windowHandles()` returns a `Set<String>` (handles are unique). `FooterSteps.java:41` checks there are
  exactly two tabs, then that only the parent is left. `LinkedHashSet` is used so the order stays predictable.
- `ParaBankApi.OPERATIONS` is an immutable `Set.of(...)` that `ApiCoverageTest` compares with the OpenAPI spec.
- `TestDataRepository.java:75`: a `Set` of keys finds template rows missing from the working workbook (O(1) `contains`).

**Q: Where did you use `LinkedHashMap`, `TreeMap`, `TreeSet`, and why those over `HashMap`/`HashSet`?**
A: When order is part of the result:
- `LinkedHashMap` keeps insertion order. `RunHistory.java:21-22` keeps scenarios in execution order for the combined
  report, `ExcelUtil` keeps column order when reading a row, and `PlaywrightUiDriver.java:46` keeps tabs in opening order.
- `TreeMap` sorts by key. `TransferLedger.java:27` gives the expected balance change per account, sorted by account
  number, so the log and the Excel output are stable and easy to compare.
- `TreeSet` with a comparator. `OpenApiContract.java:50` sorts API operations by path, then method, so a coverage
  failure lists them readably.

**Q: Where did you use immutable collections?**
A: `List.copyOf` in constructors and getters (`RetryOrchestrator.java:42`, `MavenRunExecutor.java:34`,
`RunHistory.java:37`) and `Collections.unmodifiableMap` for Excel rows (`ExcelUtil.java:193`). A caller can't change
the engine's internal state through a list it was given. A defensive copy is cheap and removes a whole class of bugs.

### Streams and functional interfaces

**Q: Show a non-trivial Stream you wrote.**
A: `pb/service/TransferLedger.expectedNetChange`: each transfer becomes two entries, (from, −amount) and
(to, +amount), with `flatMap`, then `groupingBy(account, TreeMap::new, reducing(ZERO, value, BigDecimal::add))`. The
result is the net change per account for a whole chain of transfers, which the test compares with real balances from
the API. Others:
- `CombinedReport.java:72`: `groupingBy(feature, LinkedHashMap::new, toList())` builds the report sections.
- `CombinedReport.java:79`: `toMap(run, passed, Boolean::logicalOr)`. The merge function was a real bug fix: the same
  scenario can appear twice in one run, and `toMap` without a merge function threw `IllegalStateException: Duplicate key`.
- `TransferSteps.java:277`: `toMap(Function.identity(), api::balance)` gives balances for a set of accounts.
- `ApiSteps.java:137`: `max(comparingLong(Transaction::id))` finds the newest transaction.

**Q: Where did you pass behaviour as a parameter (lambdas, functional interfaces)?**
A:
- `ApiClient.send(Function<RequestSpecification, Response> call)` (`ApiClient.java:74`) takes "the request to make".
  On HTTP 429 it waits (`Retry-After` or back-off) and calls the same function again. One retry loop serves every
  endpoint.
- `MavenRunExecutor` takes an `IntFunction<List<String>>`: extra arguments per run number (the demo failure injection
  for runs 1..N). The orchestrator decides the policy, the executor just applies it.
- `PlaywrightUiDriver.until(Duration, Supplier<T>, String)` is a generic "poll until non-null" helper, like Selenium's
  `WebDriverWait.until`.

### Concurrency

**Q: How is your framework thread-safe with 3 parallel scenarios?**
A: Each kind of shared state has its own tool:
| Shared thing | Tool | Where |
|---|---|---|
| Browser per scenario | `ThreadLocal` | `DriverManager.java:11`, `UiSession.java:11` |
| Users registered this run | `ConcurrentHashMap` | `UserRegistry.java:17` |
| Excel workbook (one file) | `ReentrantLock` around read-modify-write | `ExcelUtil.java:32` (lines 75, 105) |
| Unique username counter | `AtomicInteger.incrementAndGet()` | `TestDataGenerator.java:19` |
| Report nodes per scenario | `ConcurrentHashMap` + `computeIfAbsent` | `ExtentCucumberPlugin.java:39-46` |
| Per-scenario objects (pages, context) | Cucumber PicoContainer creates new ones per scenario | `TestContext` |

**Q: Why a `ReentrantLock` for Excel and not `synchronized`?**
A: Both would work here. An explicit lock object, `WRITE_LOCK`, is one static guard for every method that writes the
file, and its `lock()`/`unlock()` pairs sit next to the try-with-resources that opens the workbook. Without it, two
threads would each read the file, change different rows and save, and the second save would erase the first one's
row (a lost update). Excel files are not transactional.

**Q: Where did you use `ThreadLocal`, and what is the risk?**
A: `DriverManager` (one `WebDriver` per thread) and `UiSession` (one Selenium or Playwright session per thread). The
risk is leaks with thread pools: a value left behind is seen by the next test on that thread. So `quit()` always
calls `remove()` in a `finally`, and the `@After(order = 0)` hook always calls `quit()`.

### Modern Java

**Q: Where did you use records?**
A: For immutable data with value equality:
- `Locator(strategy, value)`;
- `Account`, `Transaction`, `Customer`, `LoanResult`;
- `ScenarioResult`, `BrowserChoice`, `UiOptions`;
- a private `UiSession.Running(ui, stopper)`.
Records give `equals`/`hashCode` for free, which the tests rely on (`containsExactly(new BrowserChoice(...))`).
Compact constructors validate input (`Locator`, `BrowserChoice`, `UiOptions` reject nulls and blanks).

**Q: Where did you use switch expressions?**
A: Mapping one enum to one result, where the compiler checks every case is covered:
- `Locator.parse` (prefix to strategy);
- `SeleniumUiDriver.toBy` and `PlaywrightUiDriver.selector` (strategy to engine locator);
- `UiSession.start` (engine to session);
- `DriverFactory.create` (browser to driver).
Adding a new enum constant then fails compilation in every switch that forgot it.

**Q: Where did you use `Optional`, and where did you avoid it?**
A: As a return type for "may be absent":
- `ConfigReader.find(key)`;
- `UserRegistry.find(id)`;
- `UiDriver.waitForAnyVisible(...)` (which locator appeared, or none);
- `ApiClient.retryAfter` (header present and numeric, or not).
Not used for fields or parameters, except `LoanResult.loanAccountId`, where "no account because the loan was denied"
is part of the domain.

**Q: Why `BigDecimal` for money?**
A: `0.1 + 0.2` in `double` is `0.30000000000000004`. Balances are compared exactly (`balance changed by exactly 12.34`),
so every amount is a `BigDecimal` (`Account`, `TransferCase`, `TransferLedger`, `ApiSteps`) and compared with
`compareTo`/`isEqualByComparingTo`. `equals` would make `1.0` and `1.00` different.

**Q: Where did you use regular expressions?**
A:
- `TestDataRepository.java:36`: `\{(U\d+)}` replaces `{U01}` in the LoginNegative sheet with U01's real username.
- `Hooks.java:36`: the demo's `-Dinject.failure` is a regex over scenario names.
- A Cucumber regex step in `ApiSteps` (`(withdrawal|transfer) ... (balance plus )?(-?[0-9.]+)`).
- Jenkins parameter validation (`THREADS ==~ /^[1-9][0-9]?$/`).

**Q: Where did you use try-with-resources and `AutoCloseable`?**
A:
- Opening POI workbooks (`ExcelUtil`).
- `Files.walk`/`Files.list` streams (`MavenRunExecutor`, `CucumberJsonReader`, `ApiCoverageTest`): these hold OS file
  handles until closed.
- `PlaywrightUiDriver implements AutoCloseable`: `close()` shuts the context, browser and driver process in reverse
  order, each in a `finally`.

### Design patterns (and where)

| Pattern | Where | Why |
|---|---|---|
| Page Object | `pb/pages/*Page.java` | Locators and page actions in one place; steps read like the business flow |
| Strategy | `UiDriver` → `SeleniumUiDriver` / `PlaywrightUiDriver`, chosen by `ui.engine` | Switch engines by config (guide 13) |
| Factory | `DriverFactory.create(browser, headless)`, `PlaywrightUiDriver.launch(options)` | Browser setup (options, prefs) in one place |
| Singleton (eager, immutable) | `ParaBankConfig.get()` | Config read once; safe to share across threads |
| Dependency injection | Cucumber PicoContainer injects `TestContext` into hooks and steps | Per-scenario state without static fields |
| Template method | `RetryAwareCucumberTests` extends `AbstractTestNGCucumberTests` and overrides `scenarios()` | Filters retry scenarios; the runner's flow is unchanged |
| Dependency inversion / test double | `RunExecutor` interface; `RetryOrchestratorTest` uses a `ScriptedExecutor` | The retry logic is unit-tested in milliseconds, no Maven or browser |
| Builder-style fluent API | `TransferQueue.add(...)` returns `this`; Rest Assured `given().when().then()` | Readable setup |
| Parameter object | `UiOptions` record | One argument instead of five; easy to extend |

**Q: How did you make time and randomness testable?**
A: `TestDataGenerator` takes a `java.time.Clock` and a `Random` in its constructor (`TestDataGenerator.java:27`). The
production constructor uses the system clock and `SecureRandom`, and the test passes `Clock.fixed(...)`, so the
generated username `qa_261121183405_001` can be asserted exactly. `ApiClient` likewise takes a `Sleeper` (a functional
interface): `ApiClientRetryTest` passes `waits::add`, so the 429 retry records the waits it *would* do (7 s, 7 s from
`Retry-After`) instead of sleeping.

## Sauce Labs (cloud browsers)

**Q: How did you integrate Sauce Labs?**
A: As a third engine (`ui.engine=sauce`) next to Selenium and Playwright. `SauceLabs.create` builds the capabilities
(browser, `platformName` Windows 11 or macOS 15, `browserVersion=latest`, and `sauce:options` with credentials, job
name and build) and opens a `RemoteWebDriver` to `ondemand.us-west-1.saucelabs.com`. In the retry rotation it is one
more entry, e.g. `chrome,sauce:safari,sauce:edge`.

**Q: How do the tests show up on the Sauce dashboard with names and pass/fail?**
A: At the end of each scenario the hook calls `UiSession.reportResult(name, passed)`, which runs the Sauce JavaScript
commands `sauce:job-name=...` and `sauce:job-result=passed|failed`. Locally it does nothing; a reporting error is
logged and never fails the test.

**Q: Where are the Sauce credentials?**
A: Never in git or code. Locally in a git-ignored `.env` (mode 600), read from the environment. In Jenkins, a
credential created by JCasC from secret files (not from Jenkins' environment), bound with `withCredentials` only for
builds whose rotation uses `sauce:`, and masked in the log. A key that was ever pasted somewhere gets rotated.

**Q: Did anything surprising come out of the first cloud runs?**
A: Yes: three negative-login tests failed on Edge. Before blaming the cloud browser I re-ran them on local Chrome:
same failures. ParaBank's old "accepts any password" defect had come back that afternoon, hours after it looked
fixed. The tests were re-tagged `@knownDefect`. Lesson: a fixed defect keeps its test, and a failure on a new platform
must be reproduced on a known one before it is called platform-specific.

**Tweak: Cloud runs are slower. When do you use them?**
A: Session start costs 10–30 s each, so: local browsers for fast feedback on every change, the cloud for browsers I
don't have (Safari, Edge on Windows) and for retries in CI, where the cross-browser answer is worth the time.

## Multithreading and parallel execution: the question bank

The questions interviewers ask most about threads and parallel runs, grouped from basics to "what if". Each answer
says where it happens in this framework. Related answers earlier in this doc: Excel write-back (Excel section),
`ThreadLocal` driver (Browser section), Cucumber + TestNG parallel, and the Concurrency table above.

### A. Java threading basics (asked to check you understand what you use)

**Q: Process vs thread?**
A: A process has its own memory; threads inside one process share the heap. My framework uses both: the scenarios of
one run are **threads** in one JVM (they share static fields, so shared state must be protected), while the retry
engine starts every run as a separate **process** (`MavenRunExecutor`, `ProcessBuilder` → `mvn test`), so nothing
leaks from run 1 into run 2.

**Q: Concurrency vs parallelism?**
A: Concurrency is several tasks in progress at once (they may take turns on one core); parallelism is them really
running at the same time on several cores. Three browser scenarios are both: three threads, and the browsers are
separate OS processes working at the same time.

**Q: What is a race condition? Give a real one.**
A: The result depends on which thread gets there first. Real one here: two threads each open the Excel file, change
a different row, and save; the second save overwrites the first, so one customer's result disappears (a "lost
update"). Fixed with one lock around read-modify-write in `ExcelUtil`. Proof: with the lock removed the concurrency
test failed 3 out of 3 times.

**Q: What is a deadlock and how do you avoid one?**
A: Two threads each hold a lock the other needs, so both wait forever. I avoid it by design: there is only **one**
lock in the framework (the Excel `WRITE_LOCK`), it is never held while acquiring another lock, and it is always
released in `finally`. Other shared state uses lock-free tools (`ConcurrentHashMap`, `AtomicInteger`, `ThreadLocal`).

**Q: `synchronized` vs `ReentrantLock`?**
A: Both give mutual exclusion and memory visibility. `ReentrantLock` adds `tryLock()` with a timeout, fairness, and
interruptible waiting, but you must `unlock()` in `finally` yourself. I used `ReentrantLock` in `ExcelUtil` as one
explicit static guard for every method that writes the file; `synchronized` would also have worked.

**Q: What does `volatile` do? Where did you use it?**
A: It makes a write by one thread visible to other threads (no stale cached value). It does **not** make `count++`
atomic. Used in `ApiClientRetryTest`: the test thread sets `rateLimitedAnswers` and `retryAfter`, and the JDK
`HttpServer`'s own thread reads them when it answers. Without `volatile` the server thread might not see the new
values.

**Q: `volatile` vs `AtomicInteger`?**
A: `volatile` = visibility only. `AtomicInteger` = visibility **and** atomic read-modify-write (compare-and-set).
`TestDataGenerator` uses `AtomicInteger.incrementAndGet()` for the username sequence: with a plain or volatile `int`
two threads could get the same number and create the same username. Unit test: 500 names generated from a
`parallel()` stream are all unique.

**Q: `HashMap`, `Collections.synchronizedMap`, `ConcurrentHashMap`?**
A: `HashMap` is unsafe with concurrent writers (lost entries, broken internal state). `synchronizedMap` locks the
whole map for every call. `ConcurrentHashMap` locks only small parts and has atomic helpers such as
`computeIfAbsent`. Used in `UserRegistry` (3 registration threads add customers; `UserRegistryTest` registers 200
from a parallel stream and checks all 200 are there) and `ExtentCucumberPlugin` (one report node per scenario).

**Q: Which other thread-safe collections do you know?**
A: `CopyOnWriteArrayList` (many reads, rare writes, e.g. listeners), `ConcurrentLinkedQueue`, `BlockingQueue`
(producer/consumer), `ConcurrentSkipListMap` (sorted). My `ArrayDeque` for the transfer chain is **not** thread-safe,
and doesn't need to be: each scenario has its own instance (PicoContainer creates new objects per scenario).

**Q: `ExecutorService`, `Runnable` vs `Callable`, `Future`?**
A: An `ExecutorService` is a thread pool: you submit tasks instead of creating threads. `Runnable` returns nothing,
`Callable` returns a value and may throw. `Future.get(timeout)` waits for the result and re-throws the task's
exception. In `ExcelUtilUpdateTest`: `Executors.newFixedThreadPool(5)`, 10 updates submitted, `future.get(60, SECONDS)`
on each, then `pool.shutdown()` in `finally`, then assert that all 10 rows were saved.

**Q: Why `Thread.currentThread().interrupt()` in a `catch (InterruptedException)`?**
A: Catching the exception clears the thread's "interrupted" flag. Setting it again tells the caller that someone asked
this thread to stop, instead of silently swallowing the request. Done in `ApiClient` (retry wait), `SiteGuard` (rate
limit wait) and `MavenRunExecutor` (waiting for a run).

**Q: Any trap with parallel streams?**
A: They run on the shared ForkJoin common pool, so the lambda must not touch unsafe shared state, and the order is not
guaranteed unless you use `forEachOrdered`/collect. I use them only in tests, to hammer thread-safe code
(`UserRegistryTest`, `TestDataGeneratorTest`), never in the framework itself.

### B. TestNG parallel execution

**Q: What `parallel` modes does TestNG have?**
A: `parallel="methods" | "classes" | "tests" | "instances"` with `thread-count` on the suite, plus parallel data
providers: `@DataProvider(parallel = true)` with `data-provider-thread-count`. I use only the data-provider kind,
because each Cucumber scenario is one data-provider row.

**Q: `thread-count` vs `data-provider-thread-count`?**
A: `thread-count` sizes the pool for `parallel` methods/classes/tests; `data-provider-thread-count` sizes the pool for
the rows of a parallel data provider (TestNG default 10). Mine comes from `-Dthreads` (default 3).

**Q: How exactly do you set the thread count?**
A: `mvn test -Dthreads=5`. Surefire passes it to TestNG as `dataproviderthreadcount`. Found later: an IDE (Eclipse's
TestNG plugin) does **not** pass it, so TestNG used its default of 10 browsers. Fix (D66): `RetryAwareCucumberTests`
has a `@BeforeClass` that calls `ScenarioThreads.apply(context)`, which sets the suite's data-provider thread count
from `-Dthreads`. Unit test `ScenarioThreadsTest` starts TestNG directly, measures the maximum number of rows running
at once with an `AtomicInteger`, and expects exactly 2 for `-Dthreads=2`.

**Q: Some tests must run before others (register before login). How, with parallel execution?**
A: Ordering **between** groups, parallelism **inside** them. `testng-parabank.xml` has four `<test>` blocks and no
`parallel` attribute on the suite, so the blocks run one after the other: registration → login → transfers → API.
Inside a block the scenarios run in parallel. I avoid `dependsOnMethods` between scenarios: it hides which test is
broken and makes tests impossible to run alone.

**Q: Why is the API block sequential?**
A: Its scenarios reuse the same customers and check balances. Run in parallel, one test's transfer would change
another test's expected balance. `ApiRunner` therefore keeps the normal (non-parallel) data provider.

**Q: Are `@BeforeSuite`/`@BeforeClass` run once per thread?**
A: No, once per suite/class, on one thread. Per-thread setup (a browser) belongs in per-scenario hooks: Cucumber
`@Before` starts the browser and `@After` closes it, on the scenario's own thread.

**Q: How do you choose the number of threads?**
A: Limited by the slowest resource, not by the number of tests: CPU/RAM per browser (about one browser per core
locally), and what the application allows. Here the public site rate-limits (Cloudflare 1015), so 3 is the default;
on a busy day 2. More threads past the limit makes the run **slower** (waits and retries), not faster.

### C. Selenium / Playwright in parallel

**Q: Is `WebDriver` thread-safe?**
A: No. One driver must only be used by one thread. So each scenario thread gets its own browser through a
`ThreadLocal` (`DriverManager`, `UiSession`).

**Q: What goes wrong with `public static WebDriver driver`?**
A: All threads share one variable: scenario B's `driver = new ChromeDriver()` replaces A's, A then types into B's
browser, one browser is never closed, and failures look random. It's the most common parallel bug in Selenium
frameworks.

**Q: Your `ThreadLocal` driver, what are the rules?**
A: Set at scenario start, read through `get()` (which fails clearly with the thread name if nothing was started),
and always `quit()` + `remove()` in `finally`. `start()` refuses to start a second browser on the same thread, so a
leaked browser shows up immediately instead of as "session not found" much later.

**Q: Is Playwright thread-safe?**
A: Its objects are not; Playwright's rule is one `Playwright` instance per thread. `UiSession` launches a separate
Playwright + browser per scenario thread, exactly like the Selenium side.

**Q: How do you avoid screenshots and reports overwriting each other?**
A: Screenshot files are named `<scenario>_t<threadId>_<nanoTime>.png` (`ScreenshotUtil`); Extent nodes are kept per
scenario in a `ConcurrentHashMap`; every log line shows the thread name (`[%t]` in the Log4j2 pattern), and `Hooks`
logs which browser and thread each scenario got.

**Q: How would you run on many machines (Selenium Grid / cloud)?**
A: I run on Sauce Labs: `UiSession` starts a `RemoteWebDriver` (class `SauceLabs`) instead of a local driver, stored
in the same `ThreadLocal`, so 3 scenarios run on 3 cloud browsers at once (my plan's limit), e.g. registration on 3
Safari sessions in parallel: 10/10. Pages and steps didn't change. With a bigger plan or a self-hosted Selenium Grid
(Docker images), threads can go higher because the browsers no longer use local CPU; the public site's rate limit
then becomes the real limit.

**Q: Cross-browser in parallel?**
A: Either one run per browser at the same time (separate Jenkins stages or processes with `-Dbrowser=...`), or a
browser parameter per test. I chose browser **rotation for retries** (Chrome → Firefox → Playwright) because the goal
was to tell real failures from browser-specific ones, not to double the load on a rate-limited site.

### D. Test design for parallel runs

**Q: What makes a test safe to run in parallel?**
A: It shares nothing that it changes: its own browser, its own data, no static mutable fields, no order dependency.
Here every parallel scenario owns its customers (data partitioning), per-scenario objects come from PicoContainer
(`TestContext`), and truly shared things are protected (see the Concurrency table).

**Q: How do step classes share data without statics?**
A: PicoContainer creates one `TestContext` per scenario and injects it into every step and hook class of that
scenario. Statics would be shared by all three threads.

**Q: A test passes alone but fails in parallel. How do you debug it?**
A: 1) Run it with `-Dthreads=1`; if it passes, it's an interaction, not the test logic. 2) Look for shared state:
statics, a shared file, the same test user, the same account. 3) Read the log by thread name (`[%t]`) to see what
the other threads did at that moment. 4) Check the screenshot: a real case here showed "Welcome John Smith" on a
Playwright run, a different customer's session, which pointed to the site, not the framework. 5) Prove the fix with a
test that fails without it (like the Excel lock test).

**Q: Does parallel execution make tests flaky?**
A: Only if they share something. Most "parallel flakiness" is shared data or a static driver. The rest is the
environment under load (here: rate limiting), which is why the framework detects 429/1015, waits, retries a limited
number of times, then fails with a clear message.

### E. Scenario ("tweak") questions

**Tweak: Run time is too long. Do you just raise threads to 10?**
A: No. Measure first: more threads hit the site's rate limit and slow everything down. Better options: cut UI steps
that the API can do (create data through REST), run the API suite separately, or move browsers to a grid.

**Tweak: Two Jenkins builds run at once on the same agent.**
A: Separate JVMs, so the in-JVM lock doesn't protect the shared Excel file or `reports/`. My Jenkins has 1 executor
(one build at a time); otherwise each build needs its own workspace and its own workbook copy.

**Tweak: Login scenarios start before registration finished. Why could that happen?**
A: If someone put `parallel="tests"` on the suite, the `<test>` blocks would run at the same time. Keep ordering
between blocks and parallelism inside them.

**Tweak: Ten parallel tests all need the "same" premium user.**
A: Give each its own user (create through the API in a before-hook), or make the shared user read-only for them, or
put those tests in one sequential block, as I did with the API block.
