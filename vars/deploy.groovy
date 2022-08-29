def call() {
    def files = findFiles(glob: 'cicd.yaml')
    println(files.length)
}