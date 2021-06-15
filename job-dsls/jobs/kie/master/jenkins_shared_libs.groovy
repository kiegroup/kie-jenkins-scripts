import org.kie.jenkins.jobdsl.Constants

String baseBranch = Constants.BRANCH
String ghOrgUnit = Constants.GITHUB_ORG_UNIT
String ghAuthTokenId = 'kie-ci-token'
String ghJenkinsfilePwd = 'kie-ci'
String repository = 'jenkins-pipeline-shared-libraries'

// Creation of folders where jobs are stored
folder('KIE')
folder("KIE/${baseBranch}")
folder("KIE/${baseBranch}/${Constants.PULL_REQUEST_FOLDER}") {
    displayName(Constants.PULL_REQUEST_FOLDER_DISPLAY_NAME)
}
def folderPath = ("KIE/${baseBranch}/" + Constants.PULL_REQUEST_FOLDER)

// jobs for master branch don't use the branch in the name
String jobName = "${folderPath}/${repository}-${baseBranch}.pr"
String repoUrl = "https://github.com/${ghOrgUnit}/${repository}/"

pipelineJob(jobName) {
    description("""Created automatically by Jenkins job DSL plugin. Do not edit manually! The changes will be lost next time the job is generated.
                |
                |Every configuration change needs to be done directly in the DSL files. See the below listed 'Seed job' for more info.
                |""".stripMargin())

    logRotator {
        numToKeep(10)
    }

    properties {
        githubProjectUrl(repoUrl)
    }

    definition {
        cpsScm {
            scm {
                gitSCM {
                    userRemoteConfigs {
                        userRemoteConfig {
                            url("https://github.com/\${ghprbPullAuthorLogin}/${repository}/")
                            credentialsId(ghJenkinsfilePwd)
                            name('')
                            refspec('')
                        }
                    }
                    branches {
                        branchSpec {
                            name('${ghprbSourceBranch}')
                        }
                    }
                    browser { }
                    doGenerateSubmoduleConfigurations(false)
                    gitTool('')
                }
            }
            scriptPath('.jenkins/Jenkinsfile')
        }
    }

    properties {
        pipelineTriggers {
            triggers {
                ghprbTrigger {
                    // Ordered by appearence in Jenkins UI
                    gitHubAuthId(ghAuthTokenId)
                    adminlist('')
                    useGitHubHooks(true)
                    triggerPhrase('.*[j|J]enkins,?.*(retest|test).*')
                    onlyTriggerPhrase(false)
                    autoCloseFailedPullRequests(false)
                    skipBuildPhrase(".*\\[skip\\W+ci\\].*")
                    displayBuildErrorsOnDownstreamBuilds(false)
                    cron('')
                    whitelist(ghOrgUnit)
                    orgslist(ghOrgUnit)
                    blackListLabels('')
                    whiteListLabels('')
                    allowMembersOfWhitelistedOrgsAsAdmin(true)
                    buildDescTemplate('')
                    blackListCommitAuthor('')
                    whiteListTargetBranches {
                        ghprbBranch {
                            branch(baseBranch)
                        }
                    }
                    blackListTargetBranches { }
                    includedRegions('')
                    excludedRegions('')
                    extensions {
                        ghprbSimpleStatus {
                            commitStatusContext('Build&Test')
                            addTestResults(true)
                            showMatrixStatus(false)
                            statusUrl('')
                            triggeredStatus('')
                            startedStatus('')
                        }
                        ghprbCancelBuildsOnUpdate {
                            overrideGlobal(true)
                        }
                    }
                    permitAll(false)
                    commentFilePath('')
                    msgSuccess('Success')
                    msgFailure('Failure')
                    commitStatusContext('')
                }
            }
        }
    }
}
