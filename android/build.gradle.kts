// Root build for the JsonUI Android libraries.
//
//   jsonui-core     pure Kotlin/JVM: document model, state store, dynamic values, builder DSL
//   jsonui-compose  Android library: Jetpack Compose renderer + QuickJS script engine
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
}

allprojects {
    group = "com.bclnet.jsonui"
    version = "1.0.0"
}
