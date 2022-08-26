def deployMavenJava(Map config=[:]) {
    pipeline {

        environment {
            PROJECT = "${config.project}"
            APP_NAME = "${config.appName}"
            TARGET_NAMESPACE = "${config.namespace}"
            CLUSTER_NAME = "${config.cluster}"
            CLUSTER_REGION = "${config.region}"
            CLUSTER_ZONE = "${config.zone}"
            IMAGE_TAG = "${CLUSTER_REGION}-docker.pkg.dev/${PROJECT}/anodiam-repo/${APP_NAME}:v${env.BUILD_NUMBER}"
        }

        agent {
            kubernetes {
                label "${config.appName}"
                defaultContainer 'jnlp'
                yaml """
apiVersion: v1
kind: Pod
metadata:
labels:
  component: cicd-worker-node
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
        }

        stages {
            stage('Build Artifact') {
                steps {
                    container('maven') {
                        sh("mvn clean package")
                        sh("mkdir artifact")
                        sh("cp target/*.jar artifact")
                        script {
                            if ("${config.manifestDir}") {
                                sh("cp ${config.manifestDir}/Dockerfile artifact")
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
                                sh("kubectl apply -n dev-ns -f ${config.manifestDir}")
                            } else {
                                sh("sed -i.bak 's#APP_IMAGE#${IMAGE_TAG}#' ./k8s/*.yaml")
                                sh("kubectl apply -n ${IMAGE_TAG} -f ./k8s")
                            }
                        }
                    }
                }
            }
        }
    }
}