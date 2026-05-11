plugins {
    kotlin("jvm") version "2.3.10"
    application
}

group = "com.mrpowergamerbr.butterscotchweb"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-client-java:3.4.3")
    implementation("net.lingala.zip4j:zip4j:2.11.6")
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")
    implementation("org.jsoup:jsoup:1.22.2")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

application {
    this.mainClass = "com.mrpowergamerbr.butterscotchwikigenerator.ButterscotchWikiGeneratorKt"
}

tasks.named<JavaExec>("run") {
    workingDir = file(System.getenv("RUN_FROM") ?: projectDir)
}