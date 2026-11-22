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
