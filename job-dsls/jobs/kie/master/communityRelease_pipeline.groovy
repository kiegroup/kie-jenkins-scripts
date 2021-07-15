import org.kie.jenkins.jobdsl.Constants

def kieVersion=Constants.KIE_PREFIX + ".Final"
def baseBranch=Constants.BRANCH
def releaseBranch="r7.58.0.Final"
def organization=Constants.GITHUB_ORG_UNIT
def m2Dir = Constants.LOCAL_MVN_REP
def MAVEN_OPTS="-Xms1g -Xmx3g"
def commitMsg="Upgraded version to "
def javadk=Constants.JDK_VERSION
def mvnVersion="kie-maven-" + Constants.MAVEN_VERSION
// number of build that has stored the binaries (*tar.gz) that are wanted to upload
def binariesNR=1
def toolsVer="7.46.0.Final"
def AGENT_LABEL="kie-releases"
// directory where the zip with all binaries is stored
def zipDir="\$WORKSPACE/community-deploy-dir"
// in case of testing the uploads of binaries to Nexus the URL should be https://repository.stage.jboss.org/nexus
def nexusUrl = "https://repository.jboss.org/nexus"
// in case of testing the upload of binaries to Nexus the credentials should be uploadNexus_test: recent value is for prod
def uploadCreds = "kie_upload_Nexus"
// download URL of jboss-eap for the additional tests


String EAP7_DOWNLOAD_URL = "http://download.devel.redhat.com/released/JBoss-middleware/eap7/7.3.0/jboss-eap-7.3.0.zip"

// creation of folder
folder("KIE")
folder("KIE/${baseBranch}")
folder("KIE/${baseBranch}/community-release")

def folderPath="KIE/${baseBranch}/community-release"

def comRelease='''
retry=0
pipeline {
    agent {
        label "$AGENT_LABEL"
    }
    options{
        timestamps()
    }
    tools {
        maven "$mvnVersion"
        jdk "$javadk"
    }
    stages {
        stage('CleanWorkspace') {
            steps {
                cleanWs()
            }
        }
        stage('User metadata'){
            steps {
                sh "git config --global user.email kieciuser@gmail.com"
                sh "git config --global user.name kie-ci"
            }
        }
        stage('Checkout droolsjbpm-build-bootstrap') {
            steps {
                checkout([$class: 'GitSCM', branches: [[name: '$baseBranch']], browser: [$class: 'GithubWeb', repoUrl: 'git@github.com:$organization/droolsjbpm-build-bootstrap.git'], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'droolsjbpm-build-bootstrap']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'kie-ci-user-key', url: 'git@github.com:$organization/droolsjbpm-build-bootstrap.git']]])
                dir("${WORKSPACE}" + '/droolsjbpm-build-bootstrap') {
                    sh 'pwd \\n' +
                    'git branch \\n' +
                    'git checkout -b $baseBranch\'
                }
            }    
        }   
        // checks if release branch already exists
        stage ('Check if release branch exists') {
            steps{
                sshagent(['kie-ci-user-key']) {
                    dir("${WORKSPACE}" + '/droolsjbpm-build-bootstrap') {
                        script {
                            branchExists = sh(script: 'git ls-remote --heads origin ${releaseBranch} | wc -l', returnStdout: true).trim()
                            if ( "$branchExists" == "1") {
                                echo "branch exists"
                            } else {
                                echo "branch does not exist"
                            }                             
                        } 
                    }
                }
            }
        }
        /* when release branches don't exist clone master branch */
        stage ('Clone others when release branches do not exist'){
            when{
                expression { branchExists == '0'}
            }            
            steps {
                sshagent(['kie-ci-user-key']) {
                    sh './droolsjbpm-build-bootstrap/script/release/01_cloneBranches.sh $baseBranch'
                }    
            }
        }
        /* when release branches exist clone releaseBranches */
        stage ('Clone others when release branches exist'){
            when{
                expression { branchExists == '1'}
            }
            steps {
                sshagent(['kie-ci-user-key']) {
                    dir("${WORKSPACE}" + '/droolsjbpm-build-bootstrap') {
                        sh 'git checkout $releaseBranch'
                    }    
                    sh './droolsjbpm-build-bootstrap/script/release/01_cloneBranches.sh $releaseBranch'
                }    
            }
        }
        // email send automatically when release starts and the release branches are not created
        stage ('Send email: release start') {
            when{
                expression { branchExists == '0'}
            }
            steps {
                emailext body: 'The build for community release ${kieVersion} started. \\n' +
                ' \\n' +
                '@leads: Please look at the sanity checks: https://docs.google.com/spreadsheets/d/1jPtRilvcOji__qN0QmVoXw6KSi4Nkq8Nz_coKIVfX6A/edit#gid=167259416 \\n' +
                'and assign tasks to people who should run these checks (if not done yet) \\n' +
                ' \\n' +
                'Thank you', subject: 'build for community-release $kieVersion started', to: 'bsig@redhat.com etirelli@redhat.com lazarotti@redhat.com dward@redhat.com dgutierr@redhat.com'
            }
        }
        // if release branches doesn't exist they will be created
        stage('Create release branches') {
            when{
                expression { branchExists == '0'}
            }        
            steps {
                sshagent(['kie-ci-user-key']) {
                    sh './droolsjbpm-build-bootstrap/script/release/02_createReleaseBranches.sh $releaseBranch'
                }    
            }
        }
        // part of the Maven rep will be erased
        stage ('Remove M2') {
            steps {
                sh "./droolsjbpm-build-bootstrap/script/release/eraseM2.sh $m2Dir"
            }
        }
        // poms will be upgraded to new version ($kieVersion)                         
        stage('Update versions') {
            when{
                expression { branchExists == '0'}
            }
            steps {
                echo 'kieVersion: ' + "{$kieVersion}"
                sh './droolsjbpm-build-bootstrap/script/release/03_upgradeVersions.sh $kieVersion'
            }
        }
        stage ('Add and commit version upgrades') {
            when{
                expression { branchExists == '0'}
            }        
            steps {
                echo 'kieVersion: ' + "{$kieVersion}"
                echo 'commitMsg: ' + "{$commitMsg}"                
                sh './droolsjbpm-build-bootstrap/script/release/addAndCommit.sh "$commitMsg" $kieVersion'
            }
        }
        //release branches are pushed to github
        stage('Push release branches') {
            when{
                expression { branchExists == '0'}
            }        
            steps {
                sshagent(['kie-ci-user-key']) {
                    sh './droolsjbpm-build-bootstrap/script/release/04_pushReleaseBranches.sh $releaseBranch'
                }    
            }
        }
        // mvn clean deploy of each repository to a locally directory that will be uploaded later on - this saves time        
        stage('Build & deploy repositories locally'){
            when{
                expression { repBuild == 'YES'}
            }        
            steps {
                configFileProvider([configFile(fileId: '771ff52a-a8b4-40e6-9b22-d54c7314aa1e', targetLocation: 'jenkins-settings.xml', variable: 'SETTINGS_XML_FILE')]) {
                    wrap([$class: 'Xvnc', takeScreenshot: false, useXauthority: true]) {
                        sh './droolsjbpm-build-bootstrap/script/release/05a_communityDeployLocally.sh $SETTINGS_XML_FILE'
                    }    
                }
            }
        }
        stage ('Send mail only if build fails') {
            when{
                expression { currentBuild.currentResult == 'FAILURE'}
            }        
            steps {
                emailext body: 'Status of community build for ${kieVersion} was: ' + "${currentBuild.currentResult}" +  '\\n' +
                    'Please look here: ${BUILD_URL} \\n' +
                    ' \\n' +                    
                    '${BUILD_LOG, maxLines=750}', subject: 'community-release for ${kieVersion} failed', to: 'kie-jenkins-builds@redhat.com' 
            }
        }        
        // create a local directory for archiving artifacts  
        stage('Create upload dir') {
            when{
                expression { repBuild == 'YES'}
            }        
            steps {
                script {
                    execute {
                        sh './droolsjbpm-build-bootstrap/script/release/prepareUploadDir.sh'
                        sh 'cd "${kieVersion}"_uploadBinaries \\n' +
                            'totSize=$(du -sh) \\n' +
                            'echo "Total size of directory: " $totSize >> dirSize.txt \\n' +
                            'echo "" >> dirSize.txt \\n' +
                            'ls -l >> dirSize.txt'
                    }                  
                }
            }        
        }
        // the directory of binaries will be compressed  
        stage('tar.gz uploadDir & archive artifacts'){
            when{
                expression { repBuild == 'YES'}
            }         
            steps {
                sh 'tar -czvf "${kieVersion}"_uploadBinaries.tar.gz "${kieVersion}"_uploadBinaries'
                archiveArtifacts '*.tar.gz'        
            }
        }                           
        stage('Publish JUnit test results reports') {
            when{
                expression { repBuild == 'YES'}
            }         
            steps {
                execute {
                    junit '**/target/*-reports/TEST-*.xml'
                }        
            }
        }         
        // binaries created in previous step will be compressed and uploaded to Nexus
        stage('Upload binaries to staging repository on Nexus') {
            when{
                expression { repBuild == 'YES'}
            }         
            steps {
                script {
                    execute {
                        withCredentials([usernameColonPassword(credentialsId: "$uploadCreds", variable: 'CREDS')]) {
                            sh """ 
                                cd $zipDir
                                zip -r kiegroup .
                                repoID=\\$(curl --header 'Content-Type: application/xml' -X POST -u "${CREDS}" --data '<promoteRequest><data><description>$kieVersion</description></data></promoteRequest>' -v $nexusUrl/service/local/staging/profiles/15c58a1abc895b/start | grep -oP '(?<=stagedRepositoryId)[^<]+' | sed 's/>//' | tr -d '\\n')
                                echo "repoID= " \\$repoID
                                echo " "
                                ls -al
                                echo " "
                                curl --silent --upload-file kiegroup.zip -u \\$CREDS -v \\$nexusUrl/service/local/repositories/\\$repoID/content-compressed
                                curl --header "Content-Type: application/xml" -X POST -u \\$CREDS --data "<promoteRequest><data><stagedRepositoryId>\\$repoID</stagedRepositoryId><description>$kieVersion</description></data></promoteRequest>" -v $nexusUrl/service/local/staging/profiles/15c58a1abc895b/finish 
                                """
                        }
                    }
                }            
            }
        }
        // additional tests in separate Jenkins jobs will be executed
        stage('Additional tests') {
        when{
            expression { repBuild == 'YES'}
        }            
            parallel {
                stage('jbpmTestCoverageMatrix') {
                    steps {
                        build job: "community-release-${baseBranch}-jbpmTestCoverageMatrix", propagate: false, parameters: [[$class: 'StringParameterValue', name: 'kieVersion', value: kieVersion], [$class: 'StringParameterValue', name: 'baseBranch', value: baseBranch]]    
                    }
                }
                stage('kieWbTestsMatrix') {
                    steps {
                        build job: "community-release-${baseBranch}-kieWbTestsMatrix", propagate: false, parameters: [[$class: 'StringParameterValue', name: 'kieVersion', value: kieVersion], [$class: 'StringParameterValue', name: 'baseBranch', value: baseBranch]]
                    }
                } 
                stage('kieServerMatrix') {
                    steps {
                        build job: "community-release-${baseBranch}-kieServerMatrix", propagate: false, parameters: [[$class: 'StringParameterValue', name: 'kieVersion', value: kieVersion], [$class: 'StringParameterValue', name: 'baseBranch', value: baseBranch]]    
                    }
                }                     
            }    
        }
        // load URL for repositories.jboss.org to prevent using origin-repositories.jboss.org
        stage("preload staging profiles"){
            steps{
                script {
                    execute {
                        sh 'curl --head https://proxy01-repository.jboss.org/nexus/content/groups/kie-group/org/kie/business-central/$kieVersion/business-central-$kieVersion-wildfly-deployable.zip \\n' +
                         'curl --head https://proxy02-repository.jboss.org/nexus/content/groups/kie-group/org/kie/business-central/$kieVersion/business-central-$kieVersion-wildfly-deployable.zip \\n' +                    
                         'curl --head https://proxy01-repository.jboss.org/nexus/content/groups/kie-group/org/kie/business-central/$kieVersion/business-central-$kieVersion-wildfly19.war \\n' +
                         'curl --head https://proxy02-repository.jboss.org/nexus/content/groups/kie-group/org/kie/business-central/$kieVersion/business-central-$kieVersion-wildfly19.war \\n' +
                         'curl --head https://proxy01-repository.jboss.org/nexus/content/groups/kie-group/org/kie/business-monitoring-webapp/$kieVersion/business-monitoring-webapp-$kieVersion.war \\n' +
                         'curl --head https://proxy02-repository.jboss.org/nexus/content/groups/kie-group/org/kie/business-monitoring-webapp/$kieVersion/business-monitoring-webapp-$kieVersion.war \\n' +                    
                         'curl --head https://proxy01-repository.jboss.org/nexus/content/groups/kie-group/org/kie/jbpm-server-distribution/$kieVersion/jbpm-server-distribution-$kieVersion-dist.zip \\n' +
                         'curl --head https://proxy02-repository.jboss.org/nexus/content/groups/kie-group/org/kie/jbpm-server-distribution/$kieVersion/jbpm-server-distribution-$kieVersion-dist.zip'                
                    }
                }
            }
        } 
        // send email for Sanity Checks                
        stage ('Email send with BUILD result') {
            when{
                expression { repBuild == 'YES' }
            }        
            steps {
                emailext body: 'Build of community ${kieVersion} was:  ' + "${currentBuild.currentResult}" +  '\\n' +
                    ' \\n' +
                    'Failed tests: $BUILD_URL/testReport \\n' +
                    ' \\n' +
                    ' \\n' +
                    'The artifacts are available here \\n' +
                    ' \\n' +
                    'business-central artifacts: https://repository.jboss.org/nexus/content/groups/kie-group/org/kie/business-central/$kieVersion/ \\n' +  
                    'business-central-webpp: https://repository.jboss.org/nexus/content/groups/kie-group/org/kie/business-central/$kieVersion/ \\n' +
                    'business-monitoring-webapp: https://repository.jboss.org/nexus/content/groups/kie-group/org/kie/business-monitoring-webapp/$kieVersion/ \\n' +
                    ' \\n' +
                    'Please download for sanity checks: jbpm-server-distribution.zip: https://repository.jboss.org/nexus/content/groups/kie-group/org/kie/jbpm-server-distribution/$kieVersion/ \\n' +
                    ' \\n' +                   
                    ' \\n' +
                    'Please download the needed binaries, fill in your assigned test scenarios and check the failing tests \\n' +
                    'sanity checks: https://docs.google.com/spreadsheets/d/1jPtRilvcOji__qN0QmVoXw6KSi4Nkq8Nz_coKIVfX6A/edit#gid=167259416 \\n' +
                    ' \\n' +
                    'In case Sanity Checks were already done and this kind of mail arrives the second time, please verify if the bugs reported in Sanity Checks are fixed now. \\n' + 
                    ' \\n' +
                    'KIE version: $kieVersion', subject: 'community-release-$baseBranch $kieVersion status and artefacts for sanity checks', to: 'bsig@redhat.com'
            }    
        }       
        // user interaction required: continue or abort
        stage('Approval (Point of NO return)') {
            steps {
                input message: 'Was the build stable enough to do a release', ok: 'Continue with releasing'
            }
        }
        // the tags of the release will be created and pushed to github
        stage('Push community tag') {
            steps {
                execute {
                    sshagent(['kie-ci-user-key']) {
                        script {
                            sh './droolsjbpm-build-bootstrap/script/release/08a_communityPushTags.sh'
                        }
                    }
                }        
            }
        }
        // load URL for repositories.jboss.org to prevent using origin-repositories.jboss.org
        stage("preload released profiles"){
            steps{
                script {
                    execute {
                        sh 'curl --head https://proxy01-repository.jboss.org/nexus/content/groups/public-jboss/org/kie/business-central/$kieVersion/business-central-$kieVersion-wildfly-deployable.zip \\n' +
                         'curl --head https://proxy02-repository.jboss.org/nexus/content/groups/public-jboss/org/kie/business-central/$kieVersion/business-central-$kieVersion-wildfly-deployable.zip \\n' +                    
                         'curl --head https://proxy01-repository.jboss.org/nexus/content/groups/public-jboss/org/kie/business-central/$kieVersion/business-central-$kieVersion-wildfly19.war \\n' +
                         'curl --head https://proxy02-repository.jboss.org/nexus/content/groups/public-jboss/org/kie/business-central/$kieVersion/business-central-$kieVersion-wildfly19.war \\n' +
                         'curl --head https://proxy01-repository.jboss.org/nexus/content/groups/public-jboss/org/kie/business-monitoring-webapp/$kieVersion/business-monitoring-webapp-$kieVersion.war \\n' +
                         'curl --head https://proxy02-repository.jboss.org/nexus/content/groups/public-jboss/org/kie/business-monitoring-webapp/$kieVersion/business-monitoring-webapp-$kieVersion.war \\n' +                    
                         'curl --head https://proxy01-repository.jboss.org/nexus/content/groups/public-jboss/org/kie/jbpm-server-distribution/$kieVersion/jbpm-server-distribution-$kieVersion-dist.zip \\n' +
                         'curl --head https://proxy02-repository.jboss.org/nexus/content/groups/public-jboss/org/kie/jbpm-server-distribution/$kieVersion/jbpm-server-distribution-$kieVersion-dist.zip'                
                    }
                }
            }
        }        
        stage ('Send email to BSIG') {
            steps {
                emailext body: 'The community ${kieVersion} was released. \\n' +
                ' \\n' +
                'The tags are pushed and the binaries for the webs will be uploaded soon to filemgmt.jboss.org. \\n' +
                ' \\n' +
                ' \\n' +
                'You can download the artifacts..: \\n' +
                ' \\n' +
                'business-central artifacts: https://repository.jboss.org/nexus/content/groups/public-jboss/org/kie/business-central/$kieVersion/ \\n' +
                'business-central-webapp: https://repository.jboss.org/nexus/content/groups/public-jboss/org/kie/business-central-webapp/$kieVersion/ \\n' +
                'business-monitoring-webapp: https://repository.jboss.org/nexus/content/groups/public-jboss/org/kie/business-monitoring-webapp/$kieVersion/ \\n' +
                'jbpm-server-distribution (single zip): https://repository.jboss.org/nexus/content/groups/public-jboss/org/kie/jbpm-server-distribution/$kieVersion/ \\n' +
                '\\n' +
                'Component version:\\n' +
                'kie = $kieVersion', subject: 'community-release-$baseBranch $kieVersion was released', to: 'bsig@redhat.com'
            }
        }
        // user interaction required: continue or abort
        stage('Approval(are artifacts released on Nexus?)') {
            steps {
                input message: 'Were the binaries on Nexus already released. If not please release them before continuing', ok: 'Continue with releasing'
            }
        }                 
        // if a the pipeline job was executed again but without building the binaries from uploading to filemgmt.jboss.org are needed
        stage('BUILD NUMBER of desired binaries') {
            when{
                expression { repBuild == 'NO'}
            }
            //interactive step: user should select the BUILD Nr of the artifacts to restore            
            steps {
                script {
                    binariesNR = input id: 'binariesID', message: 'Which build number has the desired binaries \\n DBN (desired build number)', parameters: [string(defaultValue: '', description: '', name: 'DBN')]
                    echo 'BUILD_NUMBER= ' + "$binariesNR"
                }    
            }
        }
        // binaries to upload to filemgmt.jbosss.org, saved in a previous build will be untared
        stage('Pull binaries of previous build') {
            when{
                expression { repBuild == 'NO'}
            } 
            steps {
                echo 'BUILD NUMBER= ' + "$binariesNR"
                step([  $class: 'CopyArtifact',
                    filter: '*.tar.gz',
                    fingerprintArtifacts: true,
                    projectName: '${JOB_NAME}',
                    selector: [$class: 'SpecificBuildSelector', buildNumber: "$binariesNR"]
                ])
                sh 'tar -xzvf "${kieVersion}"_uploadBinaries.tar.gz \\n' +
                   'ls -al'
            }        
        }
        stage('Archive artifacts') {
            when{
                expression { repBuild == 'NO'}
            }            
            steps {
                archiveArtifacts '*.tar.gz'
            }
        }        
        stage('Create jbpm installers') {
            steps {
                script {
                    execute {            
                        sh './droolsjbpm-build-bootstrap/script/release/09_createjBPM_installers.sh $toolsVer'
                    }
                }        
            }
        }
        stage('Drools binaries upload'){
            steps{
                script {
                    execute {            
                        withCredentials([sshUserPrivateKey(credentialsId: 'drools-filemgmt', keyFileVariable: 'DROOLS_FILEMGMT_KEY')]) {
                            sh './droolsjbpm-build-bootstrap/script/release/10a_drools_upload_filemgmt.sh $DROOLS_FILEMGMT_KEY'
                        }
                    }        
                }            
            }
        }        
        stage('Jbpm binaries upload'){
            steps{
                script {
                    execute {            
                        withCredentials([sshUserPrivateKey(credentialsId: 'jbpm-filemgmt', keyFileVariable: 'JBPM_FILEMGMT_KEY')]) {
                            sh './droolsjbpm-build-bootstrap/script/release/10b_jbpm_upload_filemgmt.sh $JBPM_FILEMGMT_KEY'
                        }
                    }        
                }            
            }
        } 
        stage('Optaplanner binaries upload'){
            steps{
                script {
                    execute {            
                        withCredentials([sshUserPrivateKey(credentialsId: 'optaplanner-filemgmt', keyFileVariable: 'OPTAPLANNER_FILEMGMT_KEY')]) {
                            sh './droolsjbpm-build-bootstrap/script/release/10c_optaplanner_upload_filemgmt.sh $OPTAPLANNER_FILEMGMT_KEY'
                        }
                    }        
                }            
            }
        }                                                                              
    }
}
void execute(Closure closure) {
    try {
        closure()
    } catch(error) {
        input "Retry step?"
        retry++
        echo "This is retry number ${retry}"
        execute(closure)
    } 
}
'''


pipelineJob("${folderPath}/community-release-pipeline-${baseBranch}") {

    description('this is a pipeline job for a community release /tag of all reps')


    parameters{
        stringParam("kieVersion", "${kieVersion}", "Please edit the version of kie i.e 7.51.0.Final ")
        stringParam("baseBranch", "${baseBranch}", "Please edit the name of the kie base branch ")
        stringParam("releaseBranch", "${releaseBranch}", "Please edit name of the releaseBranch - i.e. r7.51.0.Final ")
        stringParam("organization", "${organization}", "Please edit the name of organization ")
        stringParam("toolsVer", "${toolsVer}", "Please edit the latest stable version of droolsjbpm-tools<br>Important: needed for the jbpm-installer creation. Latest stable version is 7.46.0.Final.")
        choiceParam("repBuild",["YES", "NO"],"Please select if<br>you want to do a new build = YES<br>a new build is not required and artifacts are already uploaded to Nexus = NO ")
        wHideParameterDefinition {
            name('MAVEN_OPTS')
            defaultValue("${MAVEN_OPTS}")
            description('Please edit the Maven options')
        }
        wHideParameterDefinition {
            name('commitMsg')
            defaultValue("${commitMsg}")
            description('Please edit the commitMsg')
        }
        wHideParameterDefinition {
            name('m2Dir')
            defaultValue("${m2Dir}")
            description('Path to .m2/repository')
        }
        wHideParameterDefinition {
            name('binariesNR')
            defaultValue("${binariesNR}")
            description('')
        }
        wHideParameterDefinition {
            name('AGENT_LABEL')
            defaultValue("${AGENT_LABEL}")
            description('name of machine where to run this job')
        }
        wHideParameterDefinition {
            name('mvnVersion')
            defaultValue("${mvnVersion}")
            description('version of maven')
        }
        wHideParameterDefinition {
            name('javadk')
            defaultValue("${javadk}")
            description('version of jdk')
        }
        wHideParameterDefinition {
            name('zipDir')
            defaultValue("${zipDir}")
            description('Where is the zipped file to upload?')
        }
        wHideParameterDefinition {
            name('nexusUrl')
            defaultValue("${nexusUrl}")
            description('URL of Nexus server')
        }
        wHideParameterDefinition {
            name('uploadCreds')
            defaultValue("${uploadCreds}")
            description('Credentials to take for uploading binaries')
        }
    }

    logRotator {
        numToKeep(3)
    }

    definition {
        cps {
            script("${comRelease}")
            sandbox()
        }
    }

}

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Additional tests

// definition of jbpmTestCoverageMatrix test
def jbpmTestCoverage='''#!/bin/bash -e
# wget the tar.gz sources
wget -q https://repository.jboss.org/nexus/content/groups/kie-group/org/jbpm/jbpm/$kieVersion/jbpm-$kieVersion-project-sources.tar.gz -O sources.tar.gz
tar xzf sources.tar.gz
mv jbpm-$kieVersion/* .
rm -rf jbpm-$kieVersion
'''

matrixJob("${folderPath}/community-release-${baseBranch}-jbpmTestCoverageMatrix") {
    description("This job: <br> - Test coverage Matrix for jbpm <br> IMPORTANT: Created automatically by Jenkins job DSL plugin. Do not edit manually! The changes will get lost next time the job is generated.")
    parameters {
        stringParam("kieVersion", "${kieVersion}", "please edit the version of the KIE release <br> i.e. typically <b> major.minor.micro.<extension> </b>7.1.0.Beta1 for <b> community </b>or <b> major.minor.micro.<yyymmdd>-productized </b>(7.1.0.20170514-productized) for <b> productization </b> <br> Version to test. Will be supplied by the parent job. <br> Normally the KIE_VERSION will be supplied by parent job <br> ******************************************************** <br> ")
        stringParam("baseBranch", "${baseBranch}", "please edit the branch of the KIE release <br> Will be supplied by the parent job. <br> Normally the baseBranch will be supplied by parent job <br> ******************************************************** <br> ")
        wHideParameterDefinition {
            name('target')
            defaultValue("community")
            description("community")
        }
    }

    label('kie-rhel7&&kie-mem8g&&!master')

    axes {
        labelExpression("label-exp","kie-linux&&kie-mem8g")
        jdk("${javadk}")
    }

    logRotator {
        numToKeep(3)
    }

    wrappers {
        timeout {
            absolute(120)
        }
        timestamps()
        colorizeOutput()
        preBuildCleanup()
        configFiles {
            mavenSettings("3f317dd7-4d08-4ee4-b9bb-969c309e782c"){
                variable("SETTINGS_XML_FILE")
                targetLocation("jenkins-settings.xml")
            }
        }
    }

    publishers {
        archiveJunit("**/target/*-reports/TEST-*.xml")
        mailer('kie-jenkins-builds@redhat.com', false, false)
        wsCleanup()
    }

    configure { project ->
        project / 'buildWrappers' << 'org.jenkinsci.plugins.proccleaner.PreBuildCleanup' {
            cleaner(class: 'org.jenkinsci.plugins.proccleaner.PsCleaner') {
                killerType 'org.jenkinsci.plugins.proccleaner.PsAllKiller'
                killer(class: 'org.jenkinsci.plugins.proccleaner.PsAllKiller')
                username 'jenkins'
            }
        }
    }

    steps {
        shell(jbpmTestCoverage)
        maven{
            mavenInstallation("${mvnVersion}")
            goals("clean verify -e -B -Dmaven.test.failure.ignore=true -Dintegration-tests")
            rootPOM("jbpm-test-coverage/pom.xml")
            mavenOpts("-Xmx3g")
            providedSettings("3f317dd7-4d08-4ee4-b9bb-969c309e782c")
        }
    }
}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

//  run additional test: kieWbTestsMatrix
def kieWbTest='''#!/bin/bash -e
# wget the tar.gz sources
wget -q https://repository.jboss.org/nexus/content/groups/kie-group/org/kie/kie-wb-distributions/$kieVersion/kie-wb-distributions-$kieVersion-project-sources.tar.gz  -O sources.tar.gz
tar xzf sources.tar.gz
rm sources.tar.gz
mv kie-wb-distributions-$kieVersion/* .
rm -rf kie-wb-distributions-$kieVersion

echo "KIE version $kieVersion - kie-wb-common"
wget -q https://repository.jboss.org/nexus/content/groups/kie-group/org/kie/workbench/kie-wb-common/$kieVersion/kie-wb-common-$kieVersion-project-sources.tar.gz -O sources.tar.gz
tar xzf sources.tar.gz
rm sources.tar.gz
mv kie-wb-common-$kieVersion/* .
rm -rf kie-wb-common-$kieVersion'''

matrixJob("${folderPath}/community-release-${baseBranch}-kieWbTestsMatrix") {
    description("This job: <br> - Runs the KIE WB integration tests on mutiple supported containers and JDKs <br> IMPORTANT: Created automatically by Jenkins job DSL plugin. Do not edit manually! The changes will get lost next time the job is generated. ")

    parameters {
        stringParam("kieVersion", "${kieVersion}", "please edit the version of the KIE release <br> i.e. typically <b> major.minor.micro.<extension> </b>7.1.0.Beta1 for <b> community </b>or <b> major.minor.micro.<yyymmdd>-productized </b>(7.1.0.20170514-productized) for <b> productization </b> <br> Version to test. Will be supplied by the parent job. <br> Normally the KIE_VERSION will be supplied by parent job <br> ******************************************************** <br> ")
        stringParam("baseBranch", "${baseBranch}", "please edit the branch of the KIE release <br> Will be supplied by the parent job. <br> Normally the baseBranch will be supplied by parent job <br> ******************************************************** <br> ")
        wHideParameterDefinition {
            name('target')
            defaultValue("community")
            description("community")
        }
    }

    axes {
        labelExpression("label_exp","kie-rhel7&&kie-mem8g&&gui-testing")
        text("container","wildfly")
        text("war","business-central")
        jdk("${javadk}")
        text("browser","firefox")
    }

    childCustomWorkspace("\${SHORT_COMBINATION}")

    label('kie-rhel7&&kie-mem8g&&!master')

    logRotator {
        numToKeep(8)
    }

    properties{
        rebuild{
            autoRebuild()
        }
    }

    throttleConcurrentBuilds {
        maxPerNode(1)
        maxTotal(5)
        throttleMatrixConfigurations()
    }

    wrappers {
        timeout {
            absolute(120)
        }
        timestamps()
        colorizeOutput()
        preBuildCleanup()
        configFiles {
            mavenSettings("3f317dd7-4d08-4ee4-b9bb-969c309e782c") {
                variable("SETTINGS_XML_FILE")
                targetLocation("jenkins-settings.xml")
            }
        }
        xvnc{
            useXauthority()
        }
    }

    configure { project ->
        project / 'buildWrappers' << 'org.jenkinsci.plugins.proccleaner.PreBuildCleanup' {
            cleaner(class: 'org.jenkinsci.plugins.proccleaner.PsCleaner') {
                killerType 'org.jenkinsci.plugins.proccleaner.PsAllKiller'
                killer(class: 'org.jenkinsci.plugins.proccleaner.PsAllKiller')
                username 'jenkins'
            }
        }
    }

    publishers {
        archiveJunit("**/target/*-reports/TEST-*.xml")
        archiveArtifacts("**/target/screenshots/*")
        mailer('kie-jenkins-builds@redhat.com', false, false)
        wsCleanup()
    }

    steps {
        shell(kieWbTest)
        maven{
            mavenInstallation("${mvnVersion}")
            goals("-nsu -B -e -fae clean verify -P\$container,\$war")
            rootPOM("business-central-tests/pom.xml")
            properties("maven.test.failure.ignore": true)
            properties("deployment.timeout.millis":"240000")
            properties("container.startstop.timeout.millis":"240000")
            properties("webdriver.firefox.bin":"/opt/tools/firefox-60esr/firefox-bin")
            mavenOpts("-Xms1024m -Xmx1536m")
            providedSettings("3f317dd7-4d08-4ee4-b9bb-969c309e782c")
        }
    }
}

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
//  run additional test: kieServerMatrix
def kieServerTest='''#!/bin/bash -e
# wget the tar.gz sources
wget -q https://repository.jboss.org/nexus/content/groups/kie-group/org/drools/droolsjbpm-integration/$kieVersion/droolsjbpm-integration-$kieVersion-project-sources.tar.gz -O sources.tar.gz
tar xzf sources.tar.gz
mv droolsjbpm-integration-$kieVersion/* .
rm -rf droolsjbpm-integration-$kieVersion'''

matrixJob("${folderPath}/community-release-${baseBranch}-kieServerMatrix") {
    description("This job: <br> - Runs the KIE Server integration tests on mutiple supported containers and JDKs <br> IMPORTANT: Created automatically by Jenkins job DSL plugin. Do not edit manually! The changes will get lost next time the job is generated. ")

    // Label which specifies which nodes this job can run on.
    label('kie-rhel7&&kie-mem8g&&!master')

    parameters {
        stringParam("kieVersion", "${kieVersion}", "please edit the version of the KIE release <br> i.e. typically <b> major.minor.micro.<extension> </b>7.1.0.Beta1 for <b> community </b>or <b> major.minor.micro.<yyymmdd>-productized </b>(7.1.0.20170514-productized) for <b> productization </b> <br> Version to test. Will be supplied by the parent job. <br> Normally the KIE_VERSION will be supplied by parent job <br> ******************************************************** <br> ")
        stringParam("baseBranch", "${baseBranch}", "please edit the branch of the KIE release <br> Will be supplied by the parent job. <br> Normally the baseBranch will be supplied by parent job <br> ******************************************************** <br> ")
        wHideParameterDefinition {
            name('target')
            defaultValue("community")
            description("community")
        }
    }

    axes {
        jdk("${javadk}")
        text("container", "wildfly","eap7","tomcat9")
        labelExpression("label_exp","kie-linux&&kie-mem8g")
    }

    childCustomWorkspace("\${SHORT_COMBINATION}")

    logRotator {
        numToKeep(3)
    }

    wrappers {
        timeout {
            absolute(120)
        }
        timestamps()
        colorizeOutput()
        preBuildCleanup()
        configFiles {
            mavenSettings("3f317dd7-4d08-4ee4-b9bb-969c309e782c") {
                variable("SETTINGS_XML_FILE")
                targetLocation("jenkins-settings.xml")
            }
        }
    }

    configure { project ->
        project / 'buildWrappers' << 'org.jenkinsci.plugins.proccleaner.PreBuildCleanup' {
            cleaner(class: 'org.jenkinsci.plugins.proccleaner.PsCleaner') {
                killerType 'org.jenkinsci.plugins.proccleaner.PsAllKiller'
                killer(class: 'org.jenkinsci.plugins.proccleaner.PsAllKiller')
                username 'jenkins'
            }
        }
    }

    publishers {
        archiveJunit("**/target/*-reports/TEST-*.xml")
        mailer('kie-jenkins-builds@redhat.com', false, false)
        wsCleanup()
    }

    steps {
        shell(kieServerTest)
        maven{
            mavenInstallation("${mvnVersion}")
            goals("-B -e -fae -nsu clean verify -P\$container -Pjenkins-pr-builder")
            rootPOM("kie-server-parent/kie-server-tests/pom.xml")
            properties("kie.server.testing.kjars.build.settings.xml":"\$SETTINGS_XML_FILE")
            properties("maven.test.failure.ignore": true)
            properties("deployment.timeout.millis":"240000")
            properties("container.startstop.timeout.millis":"240000")
            properties("eap7.download.url":EAP7_DOWNLOAD_URL)
            mavenOpts("-Xms1024m -Xmx1536m")
            providedSettings("3f317dd7-4d08-4ee4-b9bb-969c309e782c")
        }
    }
}
