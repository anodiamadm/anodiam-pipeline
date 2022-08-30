def call(String buildPack = 'maven', String appName = 'app-name-not-specified') {
    def manifestConfig=[:]
    def deploymentConfig=[:]
    def podTemplate = getPodTemplate(buildPack)
    def project = ''
    def namespace = ''
    def clusterName = ''
    def clusterRegion = ''
    def clusterZone = ''
    def manifestDir = '.'
    def imageTag = ''

    pipeline {

        options {
            buildDiscarder(logRotator(numToKeepStr: '5'))
        }

        agent {
            kubernetes {
                label "${appName}"
                defaultContainer 'jnlp'
                yaml """${podTemplate}"""
            }
        }

        stages {
            stage('Init') {
                steps {
                    script {
                        try {
                            if (fileExists('cicd.yaml')) {
                                manifestConfig = readYaml(file: "${WORKSPACE}/cicd.yaml")
                            } else {
                                error "Pipeline not configured. Please configure using cicd.yaml"
                            }
                        } catch(Exception e) {
                            error "Pipeline failed, ERROR: " + e.getMessage()
                        }
                    }
                }
            }
            stage('Environment Configuration') {
                steps {
                    script {
                        def branchNamePrefix = env.BRANCH_NAME.split("/")[0]
                        deploymentConfig = manifestConfig.branch[branchNamePrefix]
                        project = deploymentConfig.project
                        namespace = deploymentConfig.namespace
                        manifestDir = deploymentConfig.manifestDir
                        clusterName = deploymentConfig.cluster
                        clusterRegion = deploymentConfig.region
                        clusterZone = deploymentConfig.zone
                        imageTag = clusterRegion + "-docker.pkg.dev/" + project + "/anodiam-repo/" + appName + ":v" + env.BUILD_NUMBER
                        println("imageTag=" + imageTag)
                    }
                }
            }

        }
    }
}

@NonCPS
def getPodTemplate(String buildPack = 'maven') {
    if('maven' == buildPack) {
        return getMavenPodTemplate()
    } else if('gradle' == buildPack) {
        return getGradlePodTemplate()
    } else if('npm' == buildPack) {
        return getNPMPodTemplate()
    } else {
        error "Buildpack not defined/implemented"
    }
}

@NonCPS
def getMavenPodTemplate() {
    return """
apiVersion: v1
kind: Pod
metadata:
labels:
  component: cicd-maven
spec:
  serviceAccountName: jenkins-admin
  automountServiceAccountToken: false
  containers:
  - name: maven
    image: gcr.io/cloud-builders/mvn
    command:
    - cat
    tty: true
  - name: gcloud
    image: gcr.io/cloud-builders/gcloud
    command:
    - cat
    tty: true
  - name: kubectl
    image: gcr.io/cloud-builders/kubectl
    command:
    - cat
    tty: true
"""
}

@NonCPS
def getGradlePodTemplate() {
    return """
apiVersion: v1
kind: Pod
metadata:
labels:
  component: cicd-gradle
spec:
  serviceAccountName: jenkins-admin
  automountServiceAccountToken: false
  containers:
  - name: gradle
    image: gcr.io/cloud-builders/gradle
    command:
    - cat
    tty: true
  - name: gcloud
    image: gcr.io/cloud-builders/gcloud
    command:
    - cat
    tty: true
  - name: kubectl
    image: gcr.io/cloud-builders/kubectl
    command:
    - cat
    tty: true
"""
}

@NonCPS
def getNPMPodTemplate() {
    return """
apiVersion: v1
kind: Pod
metadata:
labels:
  component: cicd-nodejs
spec:
  serviceAccountName: jenkins-admin
  automountServiceAccountToken: false
  containers:
  - name: npm
    image: gcr.io/cloud-builders/npm
    command:
    - cat
    tty: true
  - name: gcloud
    image: gcr.io/cloud-builders/gcloud
    command:
    - cat
    tty: true
  - name: kubectl
    image: gcr.io/cloud-builders/kubectl
    command:
    - cat
    tty: true
"""
}