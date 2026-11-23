plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.apollo)
}

android {
    namespace = "com.efprojects.vombatsu_client"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.efprojects.vombatsu_client"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(2)
        versionName = providers.gradleProperty("versionName").getOrElse("0.1.1")
    }

    val releaseKeystore = providers.environmentVariable("RELEASE_KEYSTORE").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

apollo {
    service("vombat") {
        packageName.set("com.efprojects.vombatsu_client.graphql")
        introspection {
            endpointUrl.set("https://api.vombat.su/graphql")
            schemaFile.set(file("src/main/graphql/schema.graphqls"))
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.apollo.runtime)
    implementation(libs.paging.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.serialization.json)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
}
