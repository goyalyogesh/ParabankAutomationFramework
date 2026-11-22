# 11 — Jenkins Pipeline (local, configured as code)

Files: `Jenkinsfile` · `ci/jenkins/jenkins.yaml` · `ci/jenkins/start-jenkins.sh`

## Start it
```bash
# one time: download Jenkins + plugins into tools/jenkins/ (git-ignored)
mkdir -p tools/jenkins && cd tools/jenkins
curl -fsSLO https://get.jenkins.io/war-stable/latest/jenkins.war          # check its .sha256 too
curl -fsSL -o plugin-manager.jar https://github.com/jenkinsci/plugin-installation-manager-tool/releases/download/2.16.0/jenkins-plugin-manager-2.16.0.jar
java -jar plugin-manager.jar --war jenkins.war -d home/plugins \
     --plugins workflow-aggregator git htmlpublisher configuration-as-code timestamper junit job-dsl
cd ../..

ci/jenkins/start-jenkins.sh        # http://127.0.0.1:8081  user: admin  password: tools/jenkins/admin-password
```
Job **parabank-automation** → *Build with Parameters*.

## Why it's set up this way
| Choice | Reason |
|---|---|
| WAR inside the project, not `brew install jenkins-lts` | Project rule: everything in the project folder. Nothing installed system-wide; delete `tools/jenkins` and it's gone |
| Configuration as Code (JCasC) + Job DSL | Jenkins is reproducible from git: no clicking, no setup wizard. Rebuild `home/` from scratch and you get the same Jenkins |
| `Jenkinsfile` in the repo (pipeline from SCM) | The pipeline is versioned and reviewed with the code it runs |
| `127.0.0.1` only, login required, anonymous read off | Jenkins runs shell commands; it must never be reachable from the network without a login |
| Admin password generated on first start, file mode 600, git-ignored | No secret in git; `JENKINS_ADMIN_PASSWORD` is passed to JCasC as an environment variable |
| 1 executor, `disableConcurrentBuilds()` | Two builds at once would double the traffic to the rate-limited public site and share test customers |
| `TESTDATA_FILE` under `$JENKINS_HOME` | The workspace is replaced on checkout; keeping the workbook outside it means the 10 customers are registered once, not every build |
| Relaxed `DirectoryBrowserSupport.CSP` | Jenkins blocks scripts/styles in archived HTML, so the Extent report renders blank. Relaxed **only** on this local single-user Jenkins; on a shared server keep the default and download the report instead |
| `file://` repository by default | Builds what's committed locally; `REPO_URL=https://github.com/... ci/jenkins/start-jenkins.sh` uses GitHub instead |

## The pipeline
```
Build            mvn install -DskipTests
Unit tests       core (59) + parabank unit suite (18) → JUnit results in Jenkins (trend graph)
ParaBank suite   mvn -pl parabank-tests exec:java -Dthreads -Dthreshold -Dbrowser.rotation -Dheadless
                 → non-zero exit if the pass rate is below the threshold → build FAILED
post             archive reports/**, "Combined report" + "Run 1 Extent report" links on the build page
```
Parameters: `THREADS` (3), `THRESHOLD` (85), `BROWSER_ROTATION` (chrome,firefox,chrome-headless), `HEADLESS` (true),
`DEMO_RETRY` (false: when true, 6 logins fail in run 1 on purpose to show the retry).

The orchestrator forwards only known properties to each run. `-Dretry.passThrough=testdata.file` adds more without
changing core code (unit-tested).

## Trigger it from the command line (REST API)
```bash
P=$(cat tools/jenkins/admin-password); J=http://127.0.0.1:8081
CRUMB=$(curl -s -c /tmp/c -u admin:$P $J/crumbIssuer/api/json | python3 -c "import json,sys;d=json.load(sys.stdin);print(d['crumbRequestField']+':'+d['crumb'])")
curl -b /tmp/c -u admin:$P -H "$CRUMB" -X POST "$J/job/parabank-automation/buildWithParameters?DEMO_RETRY=true"
```
The crumb is Jenkins' CSRF protection: a POST without it is rejected.
