plugins {
    id("memorylane.android.library")
    id("memorylane.android.hilt")
}

dependencies {
    implementation(project(":core:photos:domain"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.robolectric)
}
