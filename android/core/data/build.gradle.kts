plugins {
    alias(libs.plugins.vigia.android.library)
    alias(libs.plugins.vigia.hilt)
}

android {
    namespace = "com.ramonchancay.vigia.core.data"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
