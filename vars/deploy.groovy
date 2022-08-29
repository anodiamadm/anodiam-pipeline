def call() {
    pipeline {
        agent any;
        stages {
            stage('test') {
                steps {
                    script {
                        def files = findFiles(glob: 'cicd.yaml')
                        println(files.length)
                    }
                }
            }
        }
    }
}