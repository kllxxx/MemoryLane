plugins {
    id("memorylane.android.library")
    id("memorylane.android.compose")
}

dependencies {
    // Upstream uses Material 2 FractionalThreshold / ThresholdConfig
    implementation(libs.androidx.compose.material)
}
