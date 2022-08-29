def call() {
    pipeline {
        agent any;
        stages {
            stage('test') {
                steps {
                    script {
                        def files = findFiles(glob: 'cicd.yaml')
                        if(files.length > 0) {
                            println('Found ' + files[0])
                            datas = readYaml (file: files[0])
                            println('application.name= ' + datas.application.name)
                        }
                    }
                }
            }
        }
    }
}