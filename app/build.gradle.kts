import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile as NativeZipFile

buildscript {
    repositories { mavenCentral() }
    dependencies { classpath("org.apache.commons:commons-compress:1.27.1") }
}

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

val ffmpegAbis = listOf("arm64-v8a", "x86_64")
val ffmpegModule = "io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1"
val upstreamFfmpeg by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}
val webpReplacement = layout.projectDirectory.file("native/ffmpeg-webp16k.zip")
val patchedFfmpeg = layout.buildDirectory.file("generated/ffmpeg/ffmpeg-0.18.1-webp16k.aar")
val prepareFfmpeg by tasks.registering {
    inputs.files(upstreamFfmpeg)
    inputs.file(webpReplacement)
    outputs.file(patchedFfmpeg)
    doLast {
        fun verify(file: File, expected: String) {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                var count = input.read(buffer)
                while (count >= 0) {
                    digest.update(buffer, 0, count)
                    count = input.read(buffer)
                }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == expected) {
                "Unexpected SHA-256 for ${file.name}"
            }
        }
        val original = upstreamFfmpeg.singleFile
        verify(original, "0a87ffa6cf912b0fe76c1a99b9107f543ee2f247935fae2c71f0822eb7bc5f49")
        verify(webpReplacement.asFile, "ea4af1edad3f9678cc298bd137c3761d79c9adbbd29eb9f7afd7a0d5c74c7325")
        val archives = ffmpegAbis.associateBy { "jni/$it/libffmpeg.zip.so" }
        val output = patchedFfmpeg.get().asFile.also { it.parentFile.mkdirs() }
        ZipFile(webpReplacement.asFile).use { replacements ->
            ZipFile(original).use { input ->
                check(archives.keys.all { input.getEntry(it) != null })
                ZipOutputStream(output.outputStream().buffered()).use { aar ->
                    input.entries().asSequence().forEach { entry ->
                        aar.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                        val abi = archives[entry.name]
                        if (abi == null) {
                            input.getInputStream(entry).use { it.copyTo(aar) }
                        } else {
                            val base = temporaryDir.resolve("$abi-original.zip")
                            val patched = temporaryDir.resolve("$abi-patched.zip")
                            input.getInputStream(entry).use { stream -> base.outputStream().use { stream.copyTo(it) } }
                            val expected = replacements.entries().asSequence()
                                .filter { it.name.startsWith("$abi/") }
                                .associateBy { "usr/lib/${it.name.substringAfter('/')}" }.toMutableMap()
                            check(expected.size == 5)
                            NativeZipFile.builder().setFile(base).get().use { bundle ->
                                ZipArchiveOutputStream(patched).use { zip ->
                                    bundle.entries.asSequence().forEach { member ->
                                        val newLibrary = expected.remove(member.name)
                                        if (newLibrary == null) {
                                            // Keep the exact metadata and bytes of versioned ELF symlinks.
                                            bundle.getRawInputStream(member).use { zip.addRawArchiveEntry(member, it) }
                                        } else {
                                            check(!member.isUnixSymlink)
                                            zip.putArchiveEntry(ZipArchiveEntry(member.name).apply {
                                                unixMode = member.unixMode
                                                time = member.time
                                            })
                                            replacements.getInputStream(newLibrary).use { it.copyTo(zip) }
                                            zip.closeArchiveEntry()
                                        }
                                    }
                                }
                            }
                            check(expected.isEmpty())
                            // The upstream installer refreshes its extracted bundle when the ZIP size changes.
                            check(patched.length() != entry.size)
                            patched.inputStream().buffered().use { it.copyTo(aar) }
                        }
                        aar.closeEntry()
                    }
                }
            }
        }
    }
}

android {
    namespace = "dev.musicswitcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.musicswitcher"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
        buildConfigField("String", "YT_DLP_SHA256", "\"$ytDlpSha256\"")
        ndk {
            abiFilters += ffmpegAbis
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
    add(upstreamFfmpeg.name, "$ffmpegModule@aar")
    // library:0.18.1 already supplies the patched AAR's runtime dependencies.
    implementation(files(patchedFfmpeg).builtBy(prepareFfmpeg))

    constraints {
        implementation("commons-io:commons-io:2.21.0")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")
    }
    testImplementation("junit:junit:4.13.2")
}
