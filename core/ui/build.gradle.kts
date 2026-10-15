plugins {
    id("memorylane.android.library")
    id("memorylane.android.compose")
}

dependencies {
    api(project(":core:domain"))

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
}
