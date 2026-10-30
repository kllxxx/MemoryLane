plugins {
    id("memorylane.android.library")
}

dependencies {
    // SwipeRepository and DayCompletion: the use cases here combine photos with their decisions
    api(project(":core:domain"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
}
