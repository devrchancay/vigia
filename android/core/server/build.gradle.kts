plugins {
    alias(libs.plugins.vigia.android.library)
    alias(libs.plugins.vigia.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ramonchancay.vigia.core.server"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore)
    implementation(libs.timber)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.serialization.json)
}
