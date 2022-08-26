def call(Map config=[:]) {
    script {
        if(config.buildPack === "maven-java") {
            deployMavenJava(config)
        } else {
            println("Invalid buildpack " + config.buildPack)
        }
    }
}