def call() {
    def appName = ''
    pipeline {
        agent any;
        stages {
            stage('init') {
                def files = findFiles(glob: 'cicd.yaml')
                if(files.length > 0) {
                    println('Found ' + files[0])
                    def datas = readYaml (file: "${WORKSPACE}/" + files[0])
                    appName = datas.application.name
                } else {
                    error "Pipeline not configured. Please configure using cicd.yaml"
                }
            }
            stage('test') {
                steps {
                    script {
                        println('application.name= ' + appName)
                    }
                }
            }
        }
    }
}