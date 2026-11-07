plugins {
    alias(libs.plugins.vigia.android.feature)
}

android {
    namespace = "com.ramonchancay.vigia.feature.panel"
}

dependencies {
    implementation(projects.core.data)
}
