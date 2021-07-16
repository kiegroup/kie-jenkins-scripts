/**
 * This job is a workaround for the nightly-meta-pipeline (wich was disabled) and will be removed once the UMB messages are sent again<br>
 * Instead UMB message a simple CRON job is applied.
 */

import org.kie.jenkins.jobdsl.Constants

def javadk=Constants.JDK_VERSION
def AGENT_LABEL="kie-rhel7 && kie-mem4g"
def RHBA_VERSION_PREFIX=Constants.RHBA_VERSION_PREFIX

def metaJob="""
pipeline{
    agent {
        label "$AGENT_LABEL"
    } 
    tools {
        jdk "$javadk"        
    }   
    // IMPORTANT: In case you trigger a new branch here, please create the same branch on build-configuration project
    
    stages {
        stage('trigger nightly job 7.52.x') {
            steps {
                build job: 'nightly/7.52.x', propagate: false, wait: true, parameters: [
                        [\$class: 'StringParameterValue', name: 'KIE_GROUP_DEPLOYMENT_REPO_URL', value: 'https://bxms-qe.rhev-ci-vms.eng.rdu2.redhat.com:8443/nexus/service/local/repositories/scratch-release-rhba-7.11/content-compressed'],
                        [\$class: 'StringParameterValue', name: 'UMB_VERSION', value: '711'],
                        [\$class: 'StringParameterValue', name: 'PRODUCT_VERSION', value: '7.11.0'],
                        [\$class: 'BooleanParameterValue', name: 'SKIP_TESTS', value: true]
                ]
            }
        }

        stage('trigger nightly job master') {
            steps {
                build job: 'nightly/master', propagate: false, wait: true, parameters: [
                        [\$class: 'StringParameterValue', name: 'KIE_GROUP_DEPLOYMENT_REPO_URL', value: 'https://bxms-qe.rhev-ci-vms.eng.rdu2.redhat.com:8443/nexus/service/local/repositories/scratch-release-rhba-master/content-compressed'],
                        [\$class: 'StringParameterValue', name: 'UMB_VERSION', value: 'master'],
                        [\$class: 'StringParameterValue', name: 'PRODUCT_VERSION', value: '7.12.0'],
                        [\$class: 'BooleanParameterValue', name: 'SKIP_TESTS', value: true]
                ]
            }
        }

        // Kogito prod nightlies
        stage('trigger kogito nightly job master') {
            steps {
                build job: 'kogito.nightly/master', propagate: false, wait: true, parameters: [
                        [\$class: 'StringParameterValue', name: 'RHBA_MAVEN_REPO_URL', value: 'http://bxms-qe.rhev-ci-vms.eng.rdu2.redhat.com:8081/nexus/content/repositories/rhba-master-nightly-with-upstream'],
                        [\$class: 'StringParameterValue', name: 'RHBA_VERSION_PREFIX', value: "${RHBA_VERSION_PREFIX}"],
                        [\$class: 'StringParameterValue', name: 'KOGITO_DEPLOYMENT_REPO_URL', value: 'https://bxms-qe.rhev-ci-vms.eng.rdu2.redhat.com:8443/nexus/service/local/repositories/scratch-release-kogito-master/content-compressed'],
                        [\$class: 'StringParameterValue', name: 'UMB_VERSION', value: 'master'],
                        [\$class: 'StringParameterValue', name: 'PRODUCT_VERSION', value: '7.12.0'],
                        [\$class: 'StringParameterValue', name: 'OPTAPLANNER_PRODUCT_VERSION', value: '8.6.0'],
                        [\$class: 'BooleanParameterValue', name: 'SKIP_TESTS', value: true]
                ]
            }
        }

        stage('trigger kogito nightly job 1.5.x') {
            steps {
                build job: 'kogito.nightly/1.5.x', propagate: false, wait: true, parameters: [
                        [\$class: 'StringParameterValue', name: 'RHBA_MAVEN_REPO_URL', value: 'http://bxms-qe.rhev-ci-vms.eng.rdu2.redhat.com:8081/nexus/content/repositories/rhba-7.11-nightly-with-upstream'],
                        [\$class: 'StringParameterValue', name: 'RHBA_VERSION_PREFIX', value: '7.52.1.redhat-'],
                        [\$class: 'StringParameterValue', name: 'KOGITO_DEPLOYMENT_REPO_URL', value: 'https://bxms-qe.rhev-ci-vms.eng.rdu2.redhat.com:8443/nexus/service/local/repositories/scratch-release-kogito-1.5/content-compressed'],
                        [\$class: 'StringParameterValue', name: 'UMB_VERSION', value: '15'],
                        [\$class: 'StringParameterValue', name: 'PRODUCT_VERSION', value: '1.5.0'],
                        [\$class: 'StringParameterValue', name: 'OPTAPLANNER_PRODUCT_VERSION', value: '8.5.0'],
                        [\$class: 'BooleanParameterValue', name: 'SKIP_TESTS', value: true]
                ]
            }
        }

        // Kogito-tooling prod nightlies
        stage('trigger kogito-tooling nightly job main') {
            steps {
                build job: 'kogito-tooling.nightly/main', propagate: false, wait: true, parameters: [
                        [\$class: 'StringParameterValue', name: 'DEPLOYMENT_REPO_URL', value: 'https://bxms-qe.rhev-ci-vms.eng.rdu2.redhat.com:8443/nexus/service/local/repositories/scratch-release-rhba-master/content-compressed'],
                        [\$class: 'StringParameterValue', name: 'UMB_VERSION', value: 'main'],
                        [\$class: 'StringParameterValue', name: 'PRODUCT_VERSION', value: '7.11.0'],
                        [\$class: 'BooleanParameterValue', name: 'SKIP_TESTS', value: true]
                ]
            }
        }
    }
}
"""
// creates folder if is not existing
folder("PROD")
def folderPath="PROD"

pipelineJob("${folderPath}/cron-meta-nightly-pipeline") {

    description("This job is a workaround for the nightly-meta-pipeline (wich was disabled) and will be removed once the UMB messages are sent again<br>\n" +
            "Instead UMB message a simple CRON job is applied.")

    parameters {
        wHideParameterDefinition {
            name('AGENT_LABEL')
            defaultValue("${AGENT_LABEL}")
            description('name of machine where to run this job')
        }
        wHideParameterDefinition {
            name('javadk')
            defaultValue("${javadk}")
            description('version of jdk')
        }
    }

    logRotator {
        numToKeep(5)
    }

    properties {
        pipelineTriggers {
            triggers {
                cron{
                    spec("H 15 * * *")
                }
            }
        }
    }

    definition {
        cps {
            script("${metaJob}")
            sandbox()
        }
    }
}
