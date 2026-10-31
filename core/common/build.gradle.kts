plugins {
    id("memorylane.android.library")
    id("memorylane.android.hilt")
}

dependencies {
    // The base ViewModel is part of the API of every feature ViewModel
    api(libs.androidx.lifecycle.viewmodel)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.timber)
}
