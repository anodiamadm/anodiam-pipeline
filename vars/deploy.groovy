def call(String buildPack = 'maven', String appName = 'app-name-not-specified') {
    def manifestConfig=[:]
    def deploymentConfig=[:]
    def podTemplate = getPodTemplate(buildPack)
    def imageTag = ''
    def buildRequired = true

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
                    }
                }
            }
            stage('Deployment/Rollback') {
                steps {
                    container('kubectl') {
                        sh("PYTHONUNBUFFERED=1 gcloud container clusters get-credentials ${deploymentConfig.cluster} --zone=${deploymentConfig.zone}")
                        script {
                            def deploymentType
                            try {
                                timeout(time: 5, unit: 'MINUTES') {
                                    deploymentType = input(id: 'deploymentType', message: 'Please Select Deployment Type',
                                            parameters: [[$class: 'ChoiceParameterDefinition', description:'', name:'', choices: "Deployment\nRollback"]
                                            ])
                                }
                            } catch(err) {
                                def user = err.getCauses()[0].getUser()
                                if('SYSTEM' == user.toString()) { // SYSTEM means timeout.
                                    deploymentType = 'Deployment'
                                    println("Timeout Receiving 'Deployment Type' Input - Proceeding with 'Deployment'")
                                } else {
                                    println("Aborted by: [" + user + "]")
                                }
                            }

                            if(deploymentType == 'Deployment') {
                                println("Selected deployment type = " + deploymentType)
                                imageTag = deploymentConfig.region + "-docker.pkg.dev/" + deploymentConfig.project + "/anodiam-repo/" + appName + ":v" + env.BUILD_NUMBER
                                buildRequired = true
                            } else {
                                buildRequired = false
                                println("Selected deployment type = " + deploymentType)
                                def currentImage = sh(script: "kubectl get deployment " + appName + " -n ${deploymentConfig.namespace} -o=jsonpath='{\$.spec.template.spec.containers[:1].image}' || true", returnStdout: true)
                                def rollbackImageTag
                                if(currentImage?.trim()) {
                                    println("Found app image = " + currentImage)
                                    rollbackImageTag = input(id: 'rollbackImageTag', message: 'Please Select Image Tag to Rollback',
                                            parameters: [[$class: 'ChoiceParameterDefinition', description:'', name:'', choices: "" + currentImage + "\nAnother Image"]
                                            ])
                                } else {
                                    rollbackImageTag = 'Another Image'
                                }
                                if(rollbackImageTag == "Another Image") {
                                    def defaultImageTagValue
                                    if(currentImage?.trim()) {
                                        defaultImageTagValue = currentImage.trim()
                                    } else {
                                        defaultImageTagValue = ''
                                    }
                                    def rollbackCustomImageTag = input(id: 'rollbackCustomImageTag', message: 'Please Select Custom Image Tag to Rollback',
                                            parameters: [[$class: 'StringParameterDefinition', description:'', name:'', defaultValue: defaultImageTagValue]
                                            ])
                                    imageTag = rollbackCustomImageTag
                                } else {
                                    imageTag = rollbackImageTag
                                }
                                println("Selected app image = " + imageTag)
                            }
                        }
                    }
                }

            }
            stage('Build Dependencies') {
                when {
                    expression {
                        return buildRequired;
                    }
                }
                steps {
                    container("${buildPack}") {
                        workspace = env.WORKSPACE
                        sh("mkdir dependency-ws")
                        dir("dependency-ws") {
                            git branch: 'main', url: 'https://github.com/anodiamadm/anodiam-security-commons.git'
                            script {
                                if('maven' == buildPack) {
                                    sh("mvn clean install")
                                } else if('gradle' == buildPack) {
                                    sh("gradle clean publishToMavenLocal")
                                } else if('npm' == buildPack) {
                                    sh("npm install --omit=dev")
                                    sh("npm run build")
                                    sh("cp -r build/* dependency-ws")
                                } else {
                                    error "Buildpack not defined/implemented"
                                }
                            }
                        }
                        dir(workspace)
                    }
                }
            }
            stage('Build Artifact') {
                when {
                    expression {
                        return buildRequired;
                    }
                }
                steps {
                    container("${buildPack}") {
                        sh("mkdir artifact")
                        script {
                            if('maven' == buildPack) {
                                sh("mvn clean package")
                                sh("cp target/*.jar artifact")
                            } else if('gradle' == buildPack) {
                                sh("gradle clean build")
                                sh("cp build/libs/*.jar artifact")
                            } else if('npm' == buildPack) {
                                sh("npm install --omit=dev")
                                sh("npm run build")
                                sh("cp -r build/* artifact")
                            } else {
                                error "Buildpack not defined/implemented"
                            }

                            if ("${deploymentConfig.manifestDir}") {
                                sh("cp ${deploymentConfig.manifestDir}/Dockerfile artifact")
                            } else {
                                sh("cp Dockerfile artifact")
                            }
                        }
                    }
                }
            }
            stage('Build And Push Image') {
                when {
                    expression {
                        return buildRequired;
                    }
                }
                steps {
                    container('gcloud') {
                        sh("PYTHONUNBUFFERED=1 gcloud builds submit -t ${imageTag} ./artifact")
                    }
                }
            }
            stage('Deploy Application') {
                steps {
                    container('kubectl') {
                        sh("PYTHONUNBUFFERED=1 gcloud container clusters get-credentials ${deploymentConfig.cluster} --zone=${deploymentConfig.zone}")
                        script {
                            if ("${deploymentConfig.manifestDir}") {
                                sh("sed -i.bak 's#APP_IMAGE#${imageTag}#' ${deploymentConfig.manifestDir}/base/*.yaml")
                                sh("sed -i.bak 's#APP_IMAGE#${imageTag}#' ${deploymentConfig.manifestDir}/${deploymentConfig.env}/*.yaml")
                                sh("sed -i.bak 's#APP_NAMESPACE#${deploymentConfig.namespace}#' ${deploymentConfig.manifestDir}/base/*.yaml")
                                sh("sed -i.bak 's#APP_NAMESPACE#${deploymentConfig.namespace}#' ${deploymentConfig.manifestDir}/${deploymentConfig.env}/*.yaml")
                                sh("kubectl apply -n ${deploymentConfig.namespace} -k ${deploymentConfig.manifestDir}/${deploymentConfig.env}")
                            } else {
                                sh("sed -i.bak 's#APP_IMAGE#${imageTag}#' ./k8s/base/*.yaml")
                                sh("sed -i.bak 's#APP_IMAGE#${imageTag}#' ./k8s/${deploymentConfig.env}/*.yaml")
                                sh("sed -i.bak 's#APP_NAMESPACE#${deploymentConfig.namespace}#' ./k8s/base/*.yaml")
                                sh("sed -i.bak 's#APP_NAMESPACE#${deploymentConfig.namespace}#' ./k8s/${deploymentConfig.env}/*.yaml")
                                sh("kubectl apply -n ${deploymentConfig.namespace} -k ./k8s/${deploymentConfig.env}")
                            }
                        }
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