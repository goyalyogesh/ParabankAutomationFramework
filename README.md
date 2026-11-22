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

## Run the ParaBank tests
```bash
mvn -q install -DskipTests                                    # build + install the core library once
mvn -pl parabank-tests test -Dheadless=true                   # registration (10 users) then login, 3 threads
mvn -pl parabank-tests test -Dbrowser=firefox -Dthreads=2     # other browser / thread count
mvn -pl parabank-tests test -DsuiteXmlFile=testng-registration.xml   # registration only
mvn -pl parabank-tests test -DsuiteXmlFile=testng-transfers.xml      # transfers only (needs registered users)
mvn -pl parabank-tests test -DsuiteXmlFile=testng-unit.xml           # fast unit tests, no browser
```
Results: `parabank-tests/target/reports/spark.html` (Extent), `target/cucumber/` (Cucumber HTML/JSON),
`testdata/ParaBankData.xlsx` (credentials and results written back; created from `ParaBankData-template.xlsx`).

Expected today: 35 scenarios, 30 pass, 5 fail on purpose (`@knownDefect`): the UI logs in with any credentials
(N01, N02, N07) and UI Bill Pay never debits the payer (B01, B02). See `docs/guide/08` and `09`.
The REST API suite (guide 12) adds 26 scenarios: 19 pass, 7 are known API defects.
`mvn -pl parabank-tests test -Dtest=ApiRunner` runs only the API scenarios (needs registered users).

### With the retry engine (the way CI runs it)
```bash
mvn -q install -DskipTests
mvn -pl parabank-tests exec:java -Dheadless=true        # run 1 on Chrome; below 85% → failed ones on Firefox, then headless Chrome
mvn -pl parabank-tests exec:java -Dheadless=true "-Dretry.demo.injectFailure=customer U0[1-6] can log in and out"   # demo: force a retry
```
Options: `-Dthreshold=85 -Dretry.maxRuns=3 -Dbrowser.rotation=chrome,firefox,chrome-headless`. Known defects are
excluded from the pass rate and never retried. Reports: `reports/run-N/` (Extent, Cucumber, screenshots, Maven log)
and `reports/combined/index.html` (every scenario × run). Exit code non-zero if the threshold isn't met.
See `docs/guide/10-retry-engine-and-reports.md`.

### In Jenkins (local)
`ci/jenkins/start-jenkins.sh` → http://127.0.0.1:8081 (user `admin`, password in `tools/jenkins/admin-password`),
job **parabank-automation**. One-time download steps and design: `docs/guide/11-jenkins-pipeline.md`.

## Structure
```
automation-core/   reusable library (config, Excel, data, driver, UI, API, reports, retry engine) → JFrog Artifactory
parabank-tests/    ParaBank pages, features, steps, runners
testdata/          Excel workbook (input + results written back)
docs/              guide (read in order), decisions, interview Q&A
```

## Documentation
- Requirements: [REQUIREMENTS.md](REQUIREMENTS.md)
- Start here: [docs/guide/01-project-overview.md](docs/guide/01-project-overview.md)
- Why things are the way they are: [docs/DECISIONS.md](docs/DECISIONS.md)
- Interview preparation: [docs/INTERVIEW_QA.md](docs/INTERVIEW_QA.md)
