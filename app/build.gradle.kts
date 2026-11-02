plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// A secret kept outside the repo, under the home directory; empty when it's missing.
fun secret(path: String): String = providers
    .fileContents(layout.projectDirectory.file(System.getProperty("user.home") + "/" + path))
    .asText.orNull?.trim().orEmpty()

android {
    namespace = "com.redmesastudios.imposter"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.redmesastudios.imposter"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        // The household's TVs share players through SimpleTV, with SimpleTV's key.
        buildConfigField("String", "SYNC_KEY", "\"${secret(".config/simpletv/sync_key")}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // QR codes for joining from a phone.
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
}
