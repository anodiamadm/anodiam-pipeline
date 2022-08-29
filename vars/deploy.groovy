def call() {
    def config=[:]
    try {
        def files = findFiles(glob: 'cicd.yaml')
        if (files.length > 0) {
            config = readYaml(file: "${WORKSPACE}/" + files[0])
        } else {
            error "Pipeline not configured. Please configure using cicd.yaml"
        }
    } catch(Exception e) {
        error "Pipeline failed, please check logs..."
    }

    pipeline {
        agent any;
        stages {
            stage('test') {
                steps {
                    script {
                        println('application.name=' + config.application.name)
                    }
                }
            }
        }
    }
}