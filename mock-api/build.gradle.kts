plugins {
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

// Bundle the API contract on the classpath so Swagger UI can serve it from the fat jar too.
tasks.processResources {
    from("maskinporten-api.json") { into("openapi") }
}

tasks.test {
    // The tests reseed their database from this file, so a changed file must rerun them
    inputs.file("init.sql")
}

// The tests need the compose Postgres, so `./gradlew build` leaves them out. Run them with `./gradlew :mock-api:test`.
tasks.check {
    setDependsOn(dependsOn.filterNot { it is NamedDomainObjectProvider<*> && it.name == "test" })
}

dependencies {
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    // ktor pulls in an older netty with known vulnerabilities
    implementation(platform(libs.netty.bom))
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.swagger)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.json)
    implementation(libs.hikari)
    implementation(libs.postgresql)

    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.bouncycastle.pkix)
}
