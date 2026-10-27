plugins {
    id("memorylane.android.library")
    id("memorylane.android.hilt")
}

dependencies {
    implementation(project(":core:photos:domain"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.exifinterface)
    implementation(libs.timber)

    testImplementation(libs.robolectric)
}
