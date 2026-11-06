plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    `java-test-fixtures`
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(25)
    explicitApi()
}

ktlint {
    version.set(libs.versions.ktlint.cli.get())
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
    implementation(libs.nimbus.jose.jwt)
    testFixturesImplementation(libs.bouncycastle.pkix)

    testImplementation(kotlin("test"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
