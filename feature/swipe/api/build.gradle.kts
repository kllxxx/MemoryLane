plugins {
    id("memorylane.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.core)
    // @Keep for the enum that travels in a route: the nav library looks its serializer up by name
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.core)
}
