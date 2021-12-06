def scmVars
def changeMessage
pipeline {
  agent { label 'linux' && 'java' }
  triggers {
    cron('@midnight')
  }
  options {
    skipDefaultCheckout()
    timeout(time: 10, unit: 'HOURS')
    skipStagesAfterUnstable()
  }
  environment {
    PATH = "${env.WORKSPACE}/releaseScripts/default/adds:${env.PATH}"
  }
  stages {
    stage('Checkout') {
      steps {
        script {
          scmVars = checkout scm
        }
      }
    }
    stage ('Check environment') {
      when {
        expression {
          !currentBuild.changeSets.isEmpty()
        }
      }
      steps {
        // check existence of solvers 
        sh (label: 'check solvers', 
          script: '''#!/bin/bash -xe
echo $PATH
which z3
which cvc4
which mathsat
z3 -version
cvc4 --version | head -n 3
mathsat -version
echo "All solvers available!"
''')
      }
    }
    stage('Build and run basic tests') {
      when {
        allOf {
          expression { !currentBuild.changeSets.isEmpty() }
          not { triggeredBy 'TimerTrigger' }
        }
      }
      steps {
        withMaven {
          sh 'cd trunk/source/BA_MavenParentUltimate && mvn -T 1C clean install'
        } 
      }
    }
    stage('Build and run nightly tests') {
      when {
        allOf {
          expression { !currentBuild.changeSets.isEmpty() }
          triggeredBy 'TimerTrigger'
        }
      }
      // TODO     - ensure that test attachements are published s
      steps {
        withMaven(mavenOpts: '-Xmx4g -Xss4m -ea', options: [junitPublisher(healthScaleFactor: 1.0, keepLongStdio: true)]) {
          sh 'cd trunk/source/BA_MavenParentUltimate && mvn -T 1C clean install -Pcoverage'
        }
      }
    }
    // stage('Report'){
    //   steps {
    //     junit keepLongStdio: true, testResults: 'prototype/test_results.xml'
    //     cobertura coberturaReportFile: 'prototype/cov-cobertura.xml'
    //     catchError(buildResult: 'SUCCESS', catchInterruptions: false) {
    //       //do not let coverage result errors fail the build 
    //       publishCoverage adapters: [coberturaAdapter('prototype/cov-cobertura.xml')], calculateDiffForChangeRequests: true, sourceFileResolver: sourceFiles('NEVER_STORE')
    //     }
    //   }
    // }
    stage('Run Sonar') {
      // TODO     
      // - Check how sonar scanner with pipeline and maven works OR somehow do the "Prepare SonarQube Scanner environment" step
      // - execute mvn $SONAR_MAVEN_GOAL -Pcoverage -Dsonar.host.url=$SONAR_HOST_URL -Dsonar.login=$SONAR_AUTH_TOKEN
      when {
        allOf {
          expression { !currentBuild.changeSets.isEmpty() }
          triggeredBy 'TimerTrigger'
        }
      }
      steps {
        echo "Disabled"
      }
    }
    stage('Deploy nightly build') {
      // TODO     
      // - deploy nightly build to monteverdi if build succeeds
      // - Use https://stackoverflow.com/questions/44237417/how-do-i-use-ssh-in-a-jenkins-pipeline
      when {
        allOf {
          expression { !currentBuild.changeSets.isEmpty() }
          triggeredBy 'TimerTrigger'
        }
      }
      steps {
        sh (label: 'deploy nightly', 
          script: '''#!/bin/bash
echo "Disabled"
exit 0
DATE=$(date +%Y%m%d)
DEPLOY_DIR="/var/www/localhost/htdocs/ultimate-nightly"
pushd releaseScripts/default > /dev/null
./makeFresh.sh
if [ $? -ne 0 ] ; then echo "Deploy script failed" ; exit 1 ; fi
pushd UAutomizer-linux > /dev/null
VERSION=$(./Ultimate.py --ultversion)
if [ $? -ne 0 ] ; then echo "Ultimate did not provide a version" ; exit 1 ; fi
VERSION=$(echo "$VERSION" | head -n 1 | sed 's/This is Ultimate //g ; s/origin.//g')
if [ -z "$VERSION" ] ; then echo "Ultimate did not provide a version" ; exit 1 ; fi
TARGET="${DEPLOY_DIR}/${DATE}-${VERSION}"
popd > /dev/null
mkdir "${TARGET}"
echo "Moving *.zip to ${TARGET}"
mv *.zip "${TARGET}/"
''')
      }
    }
  }
  post {
    changed {
      script {
        env.mm_color = 'danger'
        if(currentBuild.currentResult == "SUCCESS") {
            env.mm_color = 'good'
        }
        def changeLogSets = currentBuild.changeSets
        changeMessage = ""
        for (int i = 0; i < changeLogSets.size(); i++) {
            def entries = changeLogSets[i].items
            for (int j = 0; j < entries.length; j++) {
                def entry = entries[j]
                changeMessage +="  * ${entry.commitId} by ${entry.author} on ${new Date(entry.timestamp)}: ${entry.msg}\n"
            }
        }
      }
      emailext(
        body: '$DEFAULT_CONTENT',
        mimeType: 'text/plain', 
        recipientProviders: [culprits(), developers(), requestor()], 
        replyTo: 'dietsch@informatik.uni-freiburg.de', 
        subject: '$DEFAULT_SUBJECT', 
        to: "${MAIL}"
      )
      mattermostSend( 
        color: "${env.mm_color}", 
        message: """Build ${currentBuild.id} of **${java.net.URLEncoder.encode env.JOB_NAME, 'UTF-8'}** finished with **${currentBuild.currentResult}**.
#### Links
* <${env.BUILD_URL}display/redirect|Open Jenkins log>
* <${env.RUN_CHANGES_DISPLAY_URL}|Open changes in Jenkins>
* <${scmVars.GIT_URL}|Open project in GitHub>
#### Changes
${changeMessage}
""",
        text: '', 
        channel: '#botpool', 
        icon: "https://jenkins.sopranium.de/static/0e41ff2a/images/jenkins-header-logo-v2.svg"
      )
    }
  }
}
