plugins {
    alias(libs.plugins.vigia.android.feature)
}

android {
    namespace = "com.ramonchancay.vigia.feature.setup"
}

dependencies {
    implementation(projects.core.server)
    implementation(projects.core.data)
    implementation(projects.core.device)
    implementation(libs.zxing.core)
}
