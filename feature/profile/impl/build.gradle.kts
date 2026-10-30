plugins {
    id("memorylane.android.library")
    id("memorylane.android.compose")
    id("memorylane.android.hilt")
}

dependencies {
    implementation(project(":feature:profile:api"))
    implementation(project(":core:ui"))
    implementation(project(":core:common"))
    implementation(project(":core:photos:domain"))
    implementation(project(":core:domain"))

    implementation(libs.bundles.lifecycle)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
