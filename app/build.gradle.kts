import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.collektive)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.qa)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.taskTree)
}

tasks.withType<Detekt>().configureEach {
    exclude("**/ui/theme/**")
}

val javaVersion = 11
val javaTarget = JavaVersion.entries.first { it.majorVersion == javaVersion.toString() }

kotlin {
    jvmToolchain(javaVersion)
    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}

android {
    namespace = "it.unibo.collektive"
    compileSdk = 35
    defaultConfig {
        applicationId = "it.unibo.collektive"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources.excludes += "META-INF/*.md"
        resources.excludes += "META-INF/INDEX.LIST"
        resources.excludes += "META-INF/io.netty.versions.properties"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = javaTarget
        targetCompatibility = javaTarget
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.ble.ktx)
    implementation(libs.bundles.collektive)
    implementation(libs.bundles.serialization)
    implementation(libs.bundles.hivemq)
    implementation(libs.bundles.logging)
    implementation(libs.kotlinx.datetime)
    implementation(libs.mesh)
    implementation(libs.mktt)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
