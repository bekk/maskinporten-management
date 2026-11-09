// Jib shells out to Apache Commons Compress to read Zstandard-compressed layers (DHI base images use zstd), but
// Commons Compress needs the zstd-jni library on jib's own classloader to do so. Adding it as a project dependency
// is not enough: it must be on the buildscript classpath so jib itself can see it.
buildscript {
    dependencies {
        classpath(libs.zstd.jni)
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jib)
}

val dockerImage = providers.gradleProperty("dockerImage").orElse("maskinporten-management-rest-server:local")
val dockerTags = providers.gradleProperty("dockerTags").orNull
    ?.split(',')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    ?.toSet()
    ?: emptySet()

kotlin {
    jvmToolchain(25)
}

ktlint {
    version.set(libs.versions.ktlint.cli.get())
}

application {
    mainClass = "no.kartverket.maskinportenmanagement.restserver.MainKt"
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

// Runs the server with a fake filtering service, for running locally. Jib only packs main, so the fake is not in the
// image.
val local: SourceSet by sourceSets.creating
kotlin.target.compilations.named(local.name) { associateWith(kotlin.target.compilations.getByName("main")) }
kotlin.target.compilations.named("test") { associateWith(kotlin.target.compilations.getByName(local.name)) }
tasks.check { dependsOn(local.classesTaskName) }

tasks.register<JavaExec>("runLocal") {
    group = "application"
    description = "Runs the server with a fake filtering service."
    classpath = local.runtimeClasspath
    mainClass = "no.kartverket.maskinportenmanagement.restserver.LocalMainKt"
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    workingDir = rootProject.projectDir
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.test {
    // OpenApiSpecFileTest compares the served spec with this file, so a changed file must rerun the tests
    inputs.file("openapi.json")
    inputs.file(rootProject.file("digdir-maskinporten-mock-api/init.sql"))
}

tasks.register<Test>("generateOpenApiSpec") {
    group = "build"
    description = "Writes openapi.json from the spec the running server serves."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("*.OpenApiSpecFileTest") }
    systemProperty("openApiSpec.write", "true")
    outputs.upToDateWhen { false }
}

dependencies {
    implementation(project(":maskinporten-management-client"))

    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.netty)
    // ktor pulls in an older netty with known vulnerabilities
    implementation(platform(libs.netty.bom))
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.callId)
    implementation(ktorLibs.server.routingOpenapi)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jackson.databind)
    implementation(libs.logback.classic)
    implementation(libs.logstash.logback.encoder)
    implementation(libs.google.cloud.kms)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.google.cloud.kms.grpc)
    testImplementation(testFixtures(project(":maskinporten-management-client")))
}

jib {
    from {
        image = "dhi.io/eclipse-temurin:25-alpine3.24@sha256:8da329a70f7794dd59467841e7476fd96a22fa5d3146c36811d3589b5205a5d0"
    }
    to {
        image = dockerImage.get()
        tags = dockerTags
    }
    container {
        ports = listOf("8080")
        user = "1000:1000"
    }
}
