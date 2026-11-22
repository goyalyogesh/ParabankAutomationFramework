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
`core/config/ConfigReader.java`: reads settings with precedence `-D` system property > environment variable
> `config.properties`. Typed getters (`getInt`, `getDouble`, `getBoolean`, `getList`) and `find()` returning
`Optional`. The environment map is injected, so tests can fake env vars.

**Commands and results**
- Test written first → `mvn -pl automation-core test -Dtest=ConfigReaderTest` failed to compile
  (`cannot find symbol ConfigReader`) — the expected "red" step.
- After adding the class → `Tests run: 7, Failures: 0, Errors: 0` — "green".

**Docs**: guide 03, first section of `INTERVIEW_QA.md` (configuration questions).
