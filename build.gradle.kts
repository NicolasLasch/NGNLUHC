plugins {
    id("java")
}

group = "be.thespattt.ngnl"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // Purpur repository with correct URL format
    maven("https://repo.purpurmc.org/snapshots")
    // Spigot repository (required for some transitive dependencies)
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    // Optional additional repositories that might be needed
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    compileOnly("org.purpurmc.purpur", "purpur-api", "1.21.4-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Copy>("copyToServer") {
    dependsOn("jar")
    from(layout.buildDirectory.dir("libs"))
    into("server/plugins")
}

tasks.named("build") {
    finalizedBy("copyToServer")
}