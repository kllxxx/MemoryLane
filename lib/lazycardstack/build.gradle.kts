plugins {
    id("memorylane.android.library")
    id("memorylane.android.compose")
}

dependencies {
    // Upstream uses Material 2 FractionalThreshold / ThresholdConfig
    implementation(libs.androidx.compose.material)
}

android {
    lint {
        // The vendored upstream code is written against Material 2 on purpose (see the dependency above)
        disable += "UsingMaterialAndMaterial3Libraries"
    }
}
