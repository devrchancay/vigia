plugins {
    alias(libs.plugins.vigia.android.library)
    alias(libs.plugins.vigia.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ramonchancay.vigia.feature.sources"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(libs.okhttp)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
