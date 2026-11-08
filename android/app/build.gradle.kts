import java.util.Properties

plugins {
    alias(libs.plugins.vigia.android.application)
    alias(libs.plugins.vigia.android.compose)
    alias(libs.plugins.vigia.hilt)
}

// Release signing is read from keystore.properties (gitignored). Without it, release builds are unsigned,
// which is what CI produces; see keystore.properties.example.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "com.ramonchancay.vigia"

    defaultConfig {
        applicationId = "com.ramonchancay.vigia"
        versionCode = providers.gradleProperty("vigia.versionCode").get().toInt()
        versionName = providers.gradleProperty("vigia.versionName").get()
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.device)
    implementation(projects.core.server)
    implementation(projects.feature.panel)
    implementation(projects.feature.setup)
    implementation(projects.feature.sources)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.timber)
}
