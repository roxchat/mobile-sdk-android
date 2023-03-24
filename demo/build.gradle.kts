import java.text.SimpleDateFormat
import java.util.*

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

repositories {
    mavenCentral()
    maven { setUrl("https://jitpack.io") }
}

android {
    compileSdk = 33

    defaultConfig {
        applicationId = project.property("version_demo_rox").toString()
        minSdk = 21
        targetSdk = 33
        versionCode = project.property("versionCode").toString().toInt()
        versionName = project.property("versionName").toString()
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            applicationVariants.all {
                outputs.all {
                    val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                    outputImpl.outputFileName = "${gitBranchName()} (${getCurrentDate()}).apk"
                }
            }
        }
    }

    lint {
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(project(":sdk"))
    implementation("com.astuetz:pagerslidingtabstrip:1.0.1")
    implementation("com.github.bumptech.glide:glide:3.7.0")
    implementation("com.github.bumptech.glide:okhttp3-integration:1.4.0@aar")
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("com.google.android.material:material:1.1.0")
    implementation("com.google.firebase:firebase-messaging:23.0.0")
    implementation("com.google.firebase:firebase-crashlytics:18.2.4")
    implementation("com.google.firebase:firebase-analytics:20.0.0")
    implementation("com.github.thijsk:TouchImageView:v1.3.1")
    implementation("androidx.recyclerview:recyclerview:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:1.1.3")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.preference:preference:1.1.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

fun gitBranchName(): String {
    val defaultName = "debug-build"
    val builder: ProcessBuilder = ProcessBuilder()
        .directory(File(System.getProperty("user.dir")))
        .command("git", "rev-parse", "--abbrev-ref", "HEAD")

    return try {
        val process: Process = builder.start()
        val bufferedReader = process.inputStream.reader()
        return bufferedReader.readText().trim()
    } catch (exception: Exception) {
        println(exception.printStackTrace())
        defaultName
    }
}

fun getCurrentDate(): String {
    val currentDate = Date()
    return SimpleDateFormat("dd.MM.yyyy").format(currentDate)
}
