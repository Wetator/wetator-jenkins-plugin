pipeline {
    agent any
    triggers {
        pollSCM 'H 6 * * *'
    }
    options {
        buildDiscarder(logRotator(numToKeepStr: '100', artifactNumToKeepStr: '1'))
        disableConcurrentBuilds()
        timestamps()
        timeout(time: 1, unit: 'HOURS')
        skipDefaultCheckout true
    }
    tools {
        jdk 'openjdk-1.8'
        maven 'apache-maven-3.6.3'
    }
    stages {
        stage('checkout') {
            steps {
                checkout([$class: 'SubversionSCM',
                    locations: [[remote: 'http://wetator.repositoryhosting.com/svn_public/wetator_wetator/trunk/wetator-jenkins-plugin', local: '.', depthOption: 'infinity', ignoreExternalsOption: true, cancelProcessOnExternalsFail: true]],
                    quietOperation: true,
                    workspaceUpdater: [$class: 'CheckoutUpdater']])
            }
        }
        stage('build') {
            steps {
                sh "mvn -B clean install"
            }
        }
    }
    post {
        always {
            junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
            recordIssues enabledForFailure: true, sourceCodeEncoding: 'UTF-8', sourceDirectory: 'src', tools: [
                spotBugs(reportEncoding: 'UTF-8', useRankAsPriority: true),
                pmdParser(reportEncoding: 'UTF-8'),
                cpd(reportEncoding: 'UTF-8'),
                java(),
                javaDoc(),
                taskScanner(includePattern: '**/*.java, **/*.xhtml, **/*.jsp, **/*.html, **/*.js, **/*.css, **/*.xml, **/*.wet, **/*.properties', highTags: 'FIXME, XXX', normalTags: 'TODO')]
            archiveArtifacts artifacts: 'target/wetator-jenkins-plugin*.hpi', allowEmptyArchive: true, fingerprint: true
            step([$class: 'Mailer',
                notifyEveryUnstableBuild: true,
                recipients: "frank.danek@gmail.com"])
        }
    }
}