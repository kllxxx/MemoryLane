plugins {
    id("memorylane.android.library")
    id("memorylane.android.compose")
    id("memorylane.android.hilt")
}

dependencies {
    implementation(project(":feature:achievements:api"))
    implementation(project(":core:ui"))
    implementation(project(":core:photos:api"))
    implementation(project(":core:data:api"))

    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
}
