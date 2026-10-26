plugins {
    `java-library`
}

val paperApi = "io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT"

dependencies {
    implementation(project(":boxclans-api"))
    compileOnly(paperApi)
    compileOnly("me.clip:placeholderapi:2.11.6")
    // BoxCore boost API (§10.4) and clan boosts (1.3.0); only the hook classes import it, BoxCore stays optional
    compileOnly(files("../libs/boxcore-api-1.3.0.jar"))
    // LuckPerms API (§6); only hook/LuckPermsHook imports it, LuckPerms stays optional at runtime
    compileOnly("net.luckperms:api:5.4")

    testImplementation(paperApi)
    // Paper provides the SQLite driver at runtime (never shaded); tests need their own copy
    testImplementation("org.xerial:sqlite-jdbc:3.53.4.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // a fake server for command tests (no MockBukkit for Paper 1.21.1 offline)
    testImplementation("org.mockito:mockito-core:5.18.0")
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveFileName.set("BoxClans-${project.version}.jar")
    // Bundle the API classes unrelocated so consumers resolve the same classes at runtime.
    val apiJar = project(":boxclans-api").tasks.named<Jar>("jar")
    dependsOn(apiJar)
    from(apiJar.map { zipTree(it.archiveFile) }) {
        exclude("META-INF/MANIFEST.MF")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
}
