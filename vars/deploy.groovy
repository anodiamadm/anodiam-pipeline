def call(String buildPack = 'maven', String appName = 'app-name-not-specified') {
    def config=[:]
    def podTemplate = getPodTemplate(buildPack)
    def project = ''
    def namespace = ''
    def clusterName = ''
    def clusterRegion = ''
    def clusterZone = ''
    def imageTag = ''

    pipeline {
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
                                config = readYaml(file: "${WORKSPACE}/cicd.yaml")
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
                        def branchName = scm.branches[0].name.split("/")[1]
                        println("Branch Name = " + branchName)
                        //def envConfig = config.branch.feature
                        //appName = config.application.name
                        //imageTag = "${CLUSTER_REGION}-docker.pkg.dev/${PROJECT}/anodiam-repo/${APP_NAME}:v${env.BUILD_NUMBER}"
                    }
                    sh 'printenv'
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