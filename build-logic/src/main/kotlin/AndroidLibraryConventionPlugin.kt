import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            // :feature:swipe:impl -> com.kllxx.memorylane.feature.swipe.impl
            namespace = "com.kllxx.memorylane" + path.replace(':', '.')
            compileSdk { version = release(37) }
            defaultConfig {
                minSdk = 24
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
                isCoreLibraryDesugaringEnabled = true
            }
            testOptions {
                // Robolectric reads the generated test config to find the manifest and resources
                unitTests.isIncludeAndroidResources = true
            }
        }

        dependencies {
            add("coreLibraryDesugaring", libs.library("desugar-jdk-libs"))
            add("testImplementation", libs.bundle("unit-test"))
        }
    }
}
