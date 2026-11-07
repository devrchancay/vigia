plugins {
    alias(libs.plugins.vigia.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ramonchancay.vigia.core.model"
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
