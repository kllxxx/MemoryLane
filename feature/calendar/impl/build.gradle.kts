plugins {
    id("memorylane.android.library")
    id("memorylane.android.compose")
    id("memorylane.android.hilt")
}

dependencies {
    implementation(project(":feature:calendar:api"))
    implementation(project(":core:ui"))
    implementation(project(":core:photos:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))

    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
