// Declarative pipeline: build -> unit tests -> ParaBank suite with the retry engine -> reports.
// The job itself is defined in ci/jenkins/jenkins.yaml; this file is what each build runs.
pipeline {
    agent any

    parameters {
        string(name: 'THREADS', defaultValue: '3', description: 'Parallel scenarios per block')
        string(name: 'THRESHOLD', defaultValue: '85', description: 'Pass rate (%) needed; below it, failed scenarios are retried')
        string(name: 'BROWSER_ROTATION', defaultValue: 'chrome,firefox,playwright:chrome', description: 'Browser for run 1, 2, 3 ([engine:]browser[-headless]); engines: selenium (default), playwright, sauce (cloud: chrome, firefox, edge, safari)')
        booleanParam(name: 'HEADLESS', defaultValue: true, description: 'No browser windows (CI default)')
        booleanParam(name: 'DEMO_RETRY', defaultValue: false, description: 'Fail 6 login scenarios in runs 1 and 2 on purpose (threshold 100%), so run 3 (Playwright) shows the retry')
    }

    options {
        timestamps()
        timeout(time: 90, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    environment {
        // Outside the workspace so registered customers survive between builds (no 10 new users per build)
        TESTDATA_FILE = "${JENKINS_HOME}/parabank-data/ParaBankData.xlsx"
    }

    stages {
        stage('Build') {
            steps {
                sh 'mvn -B -q install -DskipTests'
            }
        }

        stage('Unit tests') {
            steps {
                sh 'mvn -B -pl automation-core test'
                sh 'mvn -B -pl parabank-tests test -DsuiteXmlFile=testng-unit.xml'
            }
            post {
                always {
                    junit 'automation-core/target/surefire-reports/*.xml, parabank-tests/target/surefire-reports/*.xml'
                }
            }
        }

        stage('ParaBank suite (retry engine)') {
            steps {
                sh 'mkdir -p "$(dirname "$TESTDATA_FILE")"'
                script {
                    // Parameters are user input: validate their format, then hand them to the shell only through
                    // environment variables inside a single-quoted script (no Groovy interpolation into sh = no injection).
                    if (!(params.THREADS ==~ /^[1-9][0-9]?$/)) { error "THREADS must be 1-99, got '${params.THREADS}'" }
                    if (!(params.THRESHOLD ==~ /^[0-9]{1,3}(\.[0-9]+)?$/)) { error "THRESHOLD must be a number, got '${params.THRESHOLD}'" }
                    if (!(params.BROWSER_ROTATION ==~ /^[a-z:,-]+$/)) { error "BROWSER_ROTATION may only contain a-z : , - (got '${params.BROWSER_ROTATION}')" }
                    // Demo: 6 injected failures out of ~47 counted scenarios stay above 85%, so force the retry path
                    withEnv(["RUN_THREADS=${params.THREADS}",
                             "RUN_THRESHOLD=${params.DEMO_RETRY ? '100' : params.THRESHOLD}",
                             "RUN_ROTATION=${params.BROWSER_ROTATION}",
                             "RUN_HEADLESS=${params.HEADLESS}",
                             "RUN_DEMO=${params.DEMO_RETRY}"]) {
                        def suite = '''
                            set -- -Dheadless="$RUN_HEADLESS" -Dthreads="$RUN_THREADS" -Dthreshold="$RUN_THRESHOLD" \
                                   -Dbrowser.rotation="$RUN_ROTATION" -Dtestdata.file="$TESTDATA_FILE" -Dretry.passThrough=testdata.file \
                                   -Dsauce.build="Jenkins $JOB_NAME #$BUILD_NUMBER"
                            if [ "$RUN_DEMO" = "true" ]; then
                                set -- "$@" "-Dretry.demo.injectFailure=customer U0[1-6] can log in and out" -Dretry.demo.injectRuns=2
                            fi
                            mvn -B -pl parabank-tests exec:java "$@"
                        '''
                        if (params.BROWSER_ROTATION.contains('sauce:')) {
                            // Only runs that use Sauce Labs get the credentials; Jenkins masks them in the log.
                            withCredentials([usernamePassword(credentialsId: 'sauce-labs',
                                    usernameVariable: 'SAUCE_USERNAME', passwordVariable: 'SAUCE_ACCESS_KEY')]) {
                                sh suite
                            }
                        } else {
                            sh suite
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'reports/**', allowEmptyArchive: true
            publishHTML(target: [reportName: 'Combined report', reportDir: 'reports/combined', reportFiles: 'index.html',
                                 keepAll: true, alwaysLinkToLastBuild: true, allowMissing: true])
            publishHTML(target: [reportName: 'Run 1 Extent report', reportDir: 'reports/run-1/reports', reportFiles: 'spark.html',
                                 keepAll: true, alwaysLinkToLastBuild: true, allowMissing: true])
        }
    }
}
