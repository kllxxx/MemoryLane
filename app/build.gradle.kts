plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("memorylane.android.hilt")
}

android {
    namespace = "com.kllxx.memorylane"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.kllxx.memorylane"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // Robolectric reads the generated test config to find the manifest and resources
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(project(":feature:onboarding:impl"))
    implementation(project(":feature:onboarding:api"))
    implementation(project(":feature:swipe:impl"))
    implementation(project(":feature:achievements:impl"))
    implementation(project(":feature:profile:impl"))
    implementation(project(":feature:swipe:api"))
    implementation(project(":feature:achievements:api"))
    implementation(project(":feature:profile:api"))
    implementation(project(":core:photos:data"))
    implementation(project(":core:photos:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.bundles.lifecycle)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.bundles.unit.test)
    testImplementation(libs.robolectric)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}