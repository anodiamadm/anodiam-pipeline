def call() {
    def config=[:]

    pipeline {
        agent any;
        stages {
            stage('init') {
                steps {
                    script {
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
                    }
                }
            }
            stage('test feature') {
                when { branch 'feature/**' }
                steps {
                    script {
                        println('application.name=' + config.application.name)
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