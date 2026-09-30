plugins {
    id("java")
    id("maven-publish")
}

group = "be.thespattt.ngnl"
// The release workflow overrides it with -Pversion=<tag>; it is also written in plugin.yml
version = (findProperty("version") as String?)?.takeIf { it != "unspecified" } ?: "1.0.0-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.purpurmc.org/snapshots")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    compileOnly("org.purpurmc.purpur", "purpur-api", "1.21.4-R0.1-SNAPSHOT")

    // The tests use Bukkit classes (YamlConfiguration, enums) without starting a server
    testImplementation("org.purpurmc.purpur", "purpur-api", "1.21.4-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    // Put the project version in plugin.yml (only that file is filtered)
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("NoGameNoLifeUHC")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

tasks.register<Copy>("copyToServer") {
    dependsOn("jar")
    from(layout.buildDirectory.dir("libs")) {
        exclude("*-sources.jar")
    }
    into("server/plugins")
}

// Local convenience: ./gradlew build also drops the jar in server/plugins (skipped on CI)
if (System.getenv("CI") == null) {
    tasks.named("build") {
        finalizedBy("copyToServer")
    }
}

publishing {
    publications {
        create<MavenPublication>("plugin") {
            artifactId = "no-game-no-life-uhc"
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/NicolasLasch/NGNLUHC")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
