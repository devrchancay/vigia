plugins {
    alias(libs.plugins.vigia.android.library)
    alias(libs.plugins.vigia.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
}

room {
    // Exported schemas are committed so future versions can write and test migrations.
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.ramonchancay.vigia.core.data"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(libs.androidx.datastore)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
