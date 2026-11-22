# Plan 05 — Jenkins Pipeline & JFrog Artifactory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One Jenkins job builds the framework, runs the unit tests and the ParaBank suite through the retry engine,
and publishes the reports. `automation-core` is published to JFrog Artifactory and consumed from there.

**Architecture:** Jenkins runs from the official WAR inside the project (`tools/jenkins/`, git-ignored), following the
rule "everything in the project folder". Its whole configuration is code: `ci/jenkins/jenkins.yaml` (JCasC: admin
user, security, PATH, the pipeline job via Job DSL), `ci/jenkins/start-jenkins.sh`, and `Jenkinsfile`.

**Spec items:** E6 (Jenkins, parameters threads/threshold/headless, archive + publish reports), E7 (JFrog).

**Order (user decision 2026-11-22):** Jenkins first; JFrog last, once the user has created the account.

---

### Task 1: Local Jenkins (no system install)
- [ ] Download `jenkins.war` (LTS) into `tools/jenkins/`, verify its SHA-256 from get.jenkins.io.
- [ ] Download `jenkins-plugin-manager.jar`; install `workflow-aggregator git htmlpublisher configuration-as-code
      timestamper junit job-dsl` into `tools/jenkins/home/plugins`.
- [ ] `.gitignore`: `tools/jenkins/`.

### Task 2: Configuration as code
- [ ] `ci/jenkins/jenkins.yaml`: local security realm, user `admin` with `${JENKINS_ADMIN_PASSWORD}`, logged-in users
      only (anonymous read off), 1 executor, `PATH+HOMEBREW=/opt/homebrew/bin`, location URL, pipeline job
      `parabank-automation` from SCM `${REPO_URL}` branch master, script path `Jenkinsfile`.
- [ ] `ci/jenkins/start-jenkins.sh`: generates the admin password once (`umask 077`, `openssl rand`), sets
      `JENKINS_HOME`, `CASC_JENKINS_CONFIG`, `REPO_URL` (default `file://<repo>`), starts on `127.0.0.1:8081` with
      `runSetupWizard=false`, `ALLOW_LOCAL_CHECKOUT=true`, relaxed `DirectoryBrowserSupport.CSP` (Extent report).

### Task 3: Jenkinsfile
- [ ] Parameters `THREADS`, `THRESHOLD`, `BROWSER_ROTATION`, `HEADLESS`, `DEMO_RETRY`; options timestamps,
      90-minute timeout, no concurrent builds.
- [ ] Stages: Build (`install -DskipTests`) → Unit tests (core + `testng-unit.xml`, `junit` publish) →
      ParaBank suite (`exec:java` with the parameters, `-Dtestdata.file=$JENKINS_HOME/parabank-data/...`,
      `-Dretry.passThrough=testdata.file`).
- [ ] Post: archive `reports/**`, publishHTML combined report and run-1 Extent report.

### Task 4: Retry engine forwards extra properties
- [ ] Test `RetryOrchestratorTest.forwardsDefaultAndExtraPropertiesThatAreSet`; implement
      `RetryOrchestrator.passThroughArguments(Properties)` reading `retry.passThrough`.

### Task 5: Verify live
- [ ] Start Jenkins; job exists; anonymous gets 403; trigger build 1 via REST (crumb); build succeeds, reports
      linked. Build 2 with `DEMO_RETRY=true` shows run 2 on Firefox.

### Task 6: JFrog Artifactory (after the user creates the account)
- [ ] User: free JFrog cloud account, Maven local repos for releases/snapshots, identity token.
- [ ] `~/.m2/settings.xml` (outside git) with `<server>` entries matching the root POM's `distributionManagement` ids.
- [ ] `mvn -pl automation-core deploy`; consume it in `parabank-tests` from Artifactory (fresh local repo test).
- [ ] Jenkins: credentials (secret text) + `withCredentials` for deploy stage on demand.

### Task 7: Docs
- [ ] Guide `11-jenkins-pipeline.md` (+ `12-jfrog-artifactory.md` later), decisions, Q&A, README, BUILD_LOG.
