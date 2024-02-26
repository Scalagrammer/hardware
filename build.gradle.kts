import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    application
    id("maven-publish")
}

group = "scg.hardware"
version = "1.0.0"

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "scg.hardware"
            artifactId = "hardware-core"
            version = "1.0.0"
            from(components["kotlin"])
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.insert-koin:koin-core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}