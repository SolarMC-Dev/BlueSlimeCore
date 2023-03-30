repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    // Local Dependencies
    compileOnly(project(":nms:abstract"))

    // Spigot API
    compileOnly("org.spigotmc:spigot-api:1.13.2-R0.1-SNAPSHOT")
}
