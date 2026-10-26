plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.dex"
            artifactId = "boxclans-api"
            version = project.version.toString()
            from(components["java"])
            pom {
                name.set("BoxClans API")
                description.set("Public API of the BoxClans plugin (clans, relations, clan points)")
            }
        }
    }
}
