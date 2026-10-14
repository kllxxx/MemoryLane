plugins {
    id("memorylane.android.library")
    id("memorylane.android.hilt")
}

dependencies {
    implementation(project(":core:photos:api"))

    implementation(libs.kotlinx.coroutines.android)
}
