plugins {
    id("com.android.library")
    id("signing")
    id("org.jetbrains.kotlin.android")
}

val versionName = project.property("sdkVersionName").toString()

android {
    compileSdk = 35
    defaultConfig {
        minSdk = 21
        testOptions.targetSdk = 35
        namespace = "chat.rox.android.sdk"

        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("proguard-rules.pro")
    }
    buildTypes {
    }

    lint {
        abortOnError = false
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    api("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.0.0-alpha.10")
    implementation("net.zetetic:sqlcipher-android:4.7.2@aar")
    implementation("androidx.sqlite:sqlite:2.3.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.7.10")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
}