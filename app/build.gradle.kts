import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ytDlpSha256 = "e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"
val ytDlpExecutable = layout.projectDirectory.file("src/main/res/raw/ytdlp")
val verifyYtDlp by tasks.registering {
    inputs.file(ytDlpExecutable)
    inputs.property("sha256", ytDlpSha256)
    doLast {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(ytDlpExecutable.asFile.readBytes()).joinToString("") { "%02x".format(it) }
        check(hash == ytDlpSha256) { "Unexpected yt-dlp SHA-256: $hash" }
    }
}

android {
    namespace = "dev.musicswitcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.musicswitcher"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "YT_DLP_SHA256", "\"$ytDlpSha256\"")
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    // The wrapper starts its bundled Python and QuickJS from nativeLibraryDir.
    packaging { jniLibs { useLegacyPackaging = true } }
}

tasks.named("preBuild") { dependsOn(verifyYtDlp) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.03.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-session:1.10.1")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    constraints {
        implementation("commons-io:commons-io:2.21.0")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    }
    testImplementation("junit:junit:4.13.2")
}
