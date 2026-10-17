plugins {
    id("memorylane.android.library")
    id("memorylane.android.hilt")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.robolectric)
}
