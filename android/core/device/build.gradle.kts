plugins {
    alias(libs.plugins.vigia.android.library)
    alias(libs.plugins.vigia.hilt)
}

android {
    namespace = "com.ramonchancay.vigia.core.device"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)
    implementation(libs.timber)

    testImplementation(libs.junit)
}
