def call() {
    def config=[:]
    def buildPack = 'maven'
    def podTemplate = """"""

    try {
        if (fileExists('cicd.yaml')) {
            config = readYaml(file: "${WORKSPACE}/cicd.yaml")
            buildPack = config.application.buildPack
            if('maven' == buildPack) {
                podTemplate = getMavenPodTemplate()
            } else if('gradle' == buildPack) {
                podTemplate = getGradlePodTemplate()
            } else if('npm' == buildPack) {
                podTemplate = getNPMPodTemplate()
            } else {
                error "Buildpack not defined/implemented"
            }
        } else {
            error "Pipeline not configured. Please configure using cicd.yaml"
        }
    } catch(Exception e) {
        error "Pipeline failed, ERROR: " + e.getMessage()
    }

    pipeline {
        agent any;
        stages {
            stage('test feature') {
                when { branch 'feature/**' }
                steps {
                    script {
                        def envConfig = config.branch.feature
                        println('Application Name=' + config.application.name)
                        println('namespace=' + envConfig.namespace)
                        println('pod template=' + podTemplate)
                    }
                }
            }
            stage('test develop') {
                when { branch 'develop' }
                steps {
                    script {
                        println('application.name=' + config.application.name)
                    }
                }
            }
        }
    }
}

static String getMavenPodTemplate() {
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

static String getGradlePodTemplate() {
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

static String getNPMPodTemplate() {
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