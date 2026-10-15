plugins {
    id("memorylane.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.coroutines.core)
}
