import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    `java-test-fixtures`
    alias(libs.plugins.kotlin.serialization)
}

extensions.configure<KotlinJvmProjectExtension> {
    explicitApi()
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.google.cloud.kms)
    implementation(libs.nimbus.jose.jwt)
    testImplementation(libs.google.cloud.kms.grpc)
    testFixturesImplementation(libs.bouncycastle.pkix)
}
