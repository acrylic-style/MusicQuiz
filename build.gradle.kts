plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "xyz.acrylicstyle"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io/") }
    maven { url = uri("https://m2.dv8tion.net/releases") }
}

val ktorVersion = "2.3.8"

dependencies {
    implementation("se.michaelthelin.spotify:spotify-web-api-java:8.3.5")
    implementation("dev.kord:kord-core:0.13.1")
    implementation("dev.kord:kord-core-voice:0.13.1")
    implementation("dev.kord:kord-voice:0.13.1")
    implementation("org.slf4j:slf4j-simple:2.0.0")
    implementation("com.charleskorn.kaml:kaml:0.53.0")
    implementation("com.sedmelluq:lavaplayer:1.3.77")
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
}

kotlin {
    jvmToolchain(17)
}

tasks {
    shadowJar {
        manifest {
            attributes("Main-Class" to "xyz.acrylicstyle.musicquiz.MainKt")
        }
    }
}
