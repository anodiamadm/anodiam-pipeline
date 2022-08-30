def call(String buildPack = 'maven') {
    def config=[:]
    def podTemplate = """"""
    def project = ''
    def appName = ''
    def namespace = ''
    def clusterName = ''
    def clusterRegion = ''
    def clusterZone = ''
    def imageTag = ''

    pipeline {
        agent {
            kubernetes {
                label "${config.appName}"
                defaultContainer 'jnlp'
                yaml getPodTemplate(buildPack)
            }
        }

        stages {
            stage('Init') {
                steps {
                    script {
                        try {
                            if (fileExists('cicd.yaml')) {
                                config = readYaml(file: "${WORKSPACE}/cicd.yaml")
                                def envConfig = config.branch.feature
                                project =
                                appName = config.application.name
                                imageTag = "${CLUSTER_REGION}-docker.pkg.dev/${PROJECT}/anodiam-repo/${APP_NAME}:v${env.BUILD_NUMBER}"
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
                    }
                    sh 'printenv'
                }
            }
            stage('Build Artifact') {
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

                            def envConfig = config.branch.feature
                            if (envConfig.manifestDir) {
                                sh("cp ${envConfig.manifestDir}/Dockerfile artifact")
                            } else {
                                sh("cp Dockerfile artifact")
                            }
                        }
                    }
                }
            }
            stage('Push image with Container Builder') {
                steps {
                    container('gcloud') {
                        sh("PYTHONUNBUFFERED=1 gcloud builds submit -t ${IMAGE_TAG} ./artifact")
                    }
                }
            }
            stage('Deploy Dev') {
                // Feature branch
                when { branch 'feature/**' }
                steps {
                    container('kubectl') {
                        sh("PYTHONUNBUFFERED=1 gcloud container clusters get-credentials ${CLUSTER_NAME} --zone=${CLUSTER_ZONE}")
                        script {
                            if ("${config.manifestDir}") {
                                sh("sed -i.bak 's#APP_IMAGE#${IMAGE_TAG}#' ${config.manifestDir}/*.yaml")
                                sh("kubectl apply -n ${TARGET_NAMESPACE} -f ${config.manifestDir}")
                            } else {
                                sh("sed -i.bak 's#APP_IMAGE#${IMAGE_TAG}#' ./k8s/*.yaml")
                                sh("kubectl apply -n ${TARGET_NAMESPACE} -f ./k8s")
                            }
                        }
                    }
                }
            }
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

static String getPodTemplate(String buildPack = 'maven') {
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