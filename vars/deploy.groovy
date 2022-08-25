def call(Map config=[:], Closure body) {
    pipeline {

        environment {
            PROJECT = "anodiamgcpproject"
            APP_NAME = "${config.appName}"
            TARGET_NAMESPACE = "dev-ns"
            CLUSTER_NAME = "cluster-anodiam-dev"
            CLUSTER_REGION = "us-central1"
            CLUSTER_ZONE = "us-central1-c"
            IMAGE_TAG = "${CLUSTER_REGION}-docker.pkg.dev/${PROJECT}/anodiam-repo/${APP_NAME}:v${env.BUILD_NUMBER}"
        }

        agent {
            kubernetes {
                label '${APP_NAME}'
                defaultContainer 'jnlp'
                yaml """
apiVersion: v1
kind: Pod
metadata:
labels:
  component: cicd
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
                        sh "mvn clean package -DskipTests"
                        sh "mkdir artifact"
                        sh "cp **/*.jar artifact"
                        sh "cp Dockerfile artifact"
                    }
                }
            }
            stage('Push image with Container Builder') {
                steps {
                    container('gcloud') {
                        sh "PYTHONUNBUFFERED=1 gcloud builds submit -t ${IMAGE_TAG} ./artifact"
                    }
                }
            }
            stage('Deploy Dev') {
                // Feature branch
                when { branch 'feature/**' }
                steps {
                    container('kubectl') {
                        sh "PYTHONUNBUFFERED=1 gcloud container clusters get-credentials ${CLUSTER_NAME} --zone=${CLUSTER_ZONE}"
                        sh("sed -i.bak 's#APP_IMAGE#${IMAGE_TAG}#' ./k8s/*.yaml")
                        sh 'kubectl apply -n dev-ns -f ./k8s'
                    }
                }
            }

            body()
        }
    }
}