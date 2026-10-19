// Jib shells out to Apache Commons Compress to read Zstandard-compressed layers (DHI base images use zstd), but
// Commons Compress needs the zstd-jni library on jib's own classloader to do so. Adding it as a project dependency
// is not enough: it must be on the buildscript classpath so jib itself can see it.
buildscript {
    dependencies {
        classpath(libs.zstd.jni)
    }
}

plugins {
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

application {
    mainClass = "no.kartverket.maskinportenmanagement.restserver.MainKt"
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

val tools: SourceSet by sourceSets.creating

configurations.named(tools.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}

configurations.named(tools.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.runtimeOnly.get())
}

tasks.check {
    dependsOn(tools.classesTaskName)
}

tasks.test {
    // OpenApiSpecFileTest compares the served spec with this file, so a changed file must rerun the tests
    inputs.file("openapi.json")
}

tasks.register<JavaExec>("generateOpenApiSpec") {
    group = "build"
    description = "Writes openapi.json from the spec the running server serves."
    mainClass = "no.kartverket.maskinportenmanagement.restserver.GenerateOpenApiSpecKt"
    classpath = tools.runtimeClasspath
    workingDir = projectDir
}

dependencies {
    implementation(project(":maskinporten-management-client"))

    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.di)
    implementation(ktorLibs.server.routingOpenapi)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)
    implementation(libs.google.cloud.kms)

    "toolsImplementation"(sourceSets.main.get().output)
    "toolsImplementation"(libs.kotlinx.coroutines.core)

    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.google.cloud.kms.grpc)
    testImplementation(testFixtures(project(":maskinporten-management-client")))
}

jib {
    from {
        image = "dhi.io/eclipse-temurin:25-alpine3.24@sha256:0f947299651eea29537b025c58884ed363265277c9100dcc1b91ccdf9f2d1db3"
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
