// Top-level build file — plugins declarados pero NO aplicados aquí.
// Cada módulo los aplica con alias(libs.plugins.*) según lo que necesite.
plugins {
    alias(libs.plugins.android.application)   apply false
    alias(libs.plugins.android.library)       apply false
    alias(libs.plugins.kotlin.android)        apply false
    alias(libs.plugins.kotlin.compose)        apply false
    alias(libs.plugins.kotlin.serialization)  apply false
    alias(libs.plugins.hilt)                  apply false
    alias(libs.plugins.ksp)                   apply false
}