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
mvn -pl parabank-tests test -DsuiteXmlFile=testng-unit.xml           # fast unit tests, no browser
```
Results: `parabank-tests/target/reports/spark.html` (Extent), `target/cucumber/` (Cucumber HTML/JSON),
`testdata/ParaBankData.xlsx` (credentials and results written back).

Expected today: 28 scenarios, 25 pass, 3 fail on purpose (`@knownDefect`: ParaBank's UI logs in with any credentials).

## Structure
```
automation-core/   reusable library (config, Excel, data, driver, UI abstraction)  → JFrog Artifactory
parabank-tests/    ParaBank pages, features, steps, runners
testdata/          Excel workbook (input + results written back)
docs/              guide (read in order), decisions, interview Q&A
```

## Documentation
- Requirements: [REQUIREMENTS.md](REQUIREMENTS.md)
- Start here: [docs/guide/01-project-overview.md](docs/guide/01-project-overview.md)
- Why things are the way they are: [docs/DECISIONS.md](docs/DECISIONS.md)
- Interview preparation: [docs/INTERVIEW_QA.md](docs/INTERVIEW_QA.md)
