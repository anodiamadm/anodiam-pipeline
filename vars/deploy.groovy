def call() {
    def config=[:]
    def buildPack = 'maven'
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
                                buildPack = config.application.buildPack
                            } else {
                                error "Pipeline not configured. Please configure using cicd.yaml"
                            }
                        } catch(Exception e) {
                            error "Pipeline failed, ERROR: " + e.getMessage()
                        }
                    }
                }
            }
            stage('test feature') {
                when { branch 'feature/**' }
                def envConfig = config.branch.feature
                steps {
                    script {
                        println('Application Name=' + config.application.name)
                        println('namespace=' + envConfig.namespace)
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