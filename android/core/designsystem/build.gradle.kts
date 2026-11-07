plugins {
    alias(libs.plugins.vigia.android.library)
    alias(libs.plugins.vigia.android.compose)
}

android {
    namespace = "com.ramonchancay.vigia.core.designsystem"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
}
