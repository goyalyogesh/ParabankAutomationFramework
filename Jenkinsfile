// Declarative pipeline: build -> unit tests -> ParaBank suite with the retry engine -> reports.
// The job itself is defined in ci/jenkins/jenkins.yaml; this file is what each build runs.
pipeline {
    agent any

    parameters {
        string(name: 'THREADS', defaultValue: '3', description: 'Parallel scenarios per block')
        string(name: 'THRESHOLD', defaultValue: '85', description: 'Pass rate (%) needed; below it, failed scenarios are retried')
        string(name: 'BROWSER_ROTATION', defaultValue: 'chrome,firefox,chrome-headless', description: 'Browser for run 1, 2, 3')
        booleanParam(name: 'HEADLESS', defaultValue: true, description: 'No browser windows (CI default)')
        booleanParam(name: 'DEMO_RETRY', defaultValue: false, description: 'Fail 6 login scenarios in run 1 on purpose to show the retry')
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
                    def demo = params.DEMO_RETRY ? '"-Dretry.demo.injectFailure=customer U0[1-6] can log in and out"' : ''
                    sh """mvn -B -pl parabank-tests exec:java \
                        -Dheadless=${params.HEADLESS} \
                        -Dthreads=${params.THREADS} \
                        -Dthreshold=${params.THRESHOLD} \
                        -Dbrowser.rotation=${params.BROWSER_ROTATION} \
                        -Dtestdata.file=\$TESTDATA_FILE \
                        -Dretry.passThrough=testdata.file \
                        ${demo}"""
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
