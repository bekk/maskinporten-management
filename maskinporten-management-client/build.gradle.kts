import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

extensions.configure<KotlinJvmProjectExtension> {
    explicitApi()
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
