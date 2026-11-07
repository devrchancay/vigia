import com.android.build.api.dsl.ApplicationExtension
import com.ramonchancay.vigia.buildlogic.VigiaSdk
import com.ramonchancay.vigia.buildlogic.configureAndroidCommon
import com.ramonchancay.vigia.buildlogic.libs
import com.ramonchancay.vigia.buildlogic.pluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(libs.pluginId("android-application"))
        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(this)
            defaultConfig.targetSdk = VigiaSdk.TARGET
        }
    }
}
