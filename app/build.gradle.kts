import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val signingProperties = Properties()
val signingPropertiesFile = rootProject.file("keystore.properties")

if (signingPropertiesFile.isFile) {
    signingPropertiesFile.inputStream().use(signingProperties::load)
}

android {
    namespace = "com.y3lc.yourdiary"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.y3lc.yourdiary"
        minSdk = 24
        targetSdk = 37
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            val storeFilePath = signingProperties.getProperty("storeFile")
                ?: error("缺少 keystore.properties，无法构建签名 release APK")
            signingConfig = signingConfigs.create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = signingProperties.getProperty("storePassword")
                    ?: error("缺少 release 签名存储密码")
                keyAlias = signingProperties.getProperty("keyAlias")
                    ?: error("缺少 release 签名别名")
                keyPassword = signingProperties.getProperty("keyPassword")
                    ?: error("缺少 release 签名密钥密码")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
