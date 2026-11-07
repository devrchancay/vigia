import com.android.build.api.dsl.LibraryExtension
import com.ramonchancay.vigia.buildlogic.configureAndroidCommon
import com.ramonchancay.vigia.buildlogic.libs
import com.ramonchancay.vigia.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-library"))
        extensions.configure<LibraryExtension> {
            configureAndroidCommon(this)
            defaultConfig.consumerProguardFiles("consumer-rules.pro")
            testOptions.unitTests.isIncludeAndroidResources = true
            // Robolectric's FileDescriptor interceptor calls jdk.internal.access.SharedSecrets, which the JDK
            // does not export by default. -Xshare:off avoids the CDS warning when test agents are attached.
            testOptions.unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "-Xshare:off") }
        }
    }
}
