import com.ramonchancay.vigia.buildlogic.libs
import com.ramonchancay.vigia.buildlogic.library
import com.ramonchancay.vigia.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A feature module: library + Compose + Hilt + type-safe navigation + the standard test stack. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("vigia.android.library")
        pluginManager.apply("vigia.android.compose")
        pluginManager.apply("vigia.hilt")
        pluginManager.apply(libs.pluginId("kotlin-serialization"))
        dependencies {
            add("implementation", project(":core:common"))
            add("implementation", project(":core:designsystem"))
            add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
            add("implementation", libs.library("androidx-navigation-compose"))
            add("implementation", libs.library("kotlinx-serialization-json"))
            add("implementation", libs.library("timber"))

            add("testImplementation", libs.library("junit"))
            add("testImplementation", libs.library("kotlinx-coroutines-test"))
            add("testImplementation", libs.library("turbine"))
            add("testImplementation", libs.library("mockk"))
            add("testImplementation", libs.library("robolectric"))
            add("testImplementation", libs.library("androidx-test-ext-junit"))
            add("testImplementation", libs.library("androidx-compose-ui-test-junit4"))
            add("debugImplementation", libs.library("androidx-compose-ui-test-manifest"))
        }
    }
}
