import java.net.URL
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.impbrando.formationdrill"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.impbrando.formationdrill"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // Where the game is loaded from (edits go live without an app update)
        buildConfigField("String", "GAME_URL", "\"https://imp-brando.github.io/formation-drill/\"")
        // phones: 64- and 32-bit ARM; add the emulator ABI with -Pemulator
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") + (if (project.hasProperty("emulator")) listOf("x86_64") else emptyList()) }
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    testImplementation("junit:junit:4.13.2")
}

/* The on-device speech model (Vosk small English, Apache-2.0) is downloaded at build time, not committed. */
val modelDir = layout.projectDirectory.dir("src/main/assets/model-en-us")
val fetchModel by tasks.registering {
    outputs.dir(modelDir)
    doLast {
        val dir = modelDir.asFile
        if (File(dir, "uuid").exists()) return@doLast
        dir.mkdirs()
        val url = URL("https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip")
        ZipInputStream(url.openStream().buffered()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val rel = e.name.substringAfter('/')          // strip the top-level folder
                if (rel.isNotEmpty()) {
                    val f = File(dir, rel)
                    if (e.isDirectory) f.mkdirs() else { f.parentFile.mkdirs(); f.outputStream().use { zip.copyTo(it) } }
                }
                e = zip.nextEntry
            }
        }
        File(dir, "uuid").writeText("vosk-model-small-en-us-0.15")
    }
}
/* Bundled fallback copy of the game, for a first launch with no signal. */
val copyGame by tasks.registering(Copy::class) {
    from(rootProject.layout.projectDirectory.file("../index.html"), rootProject.layout.projectDirectory.file("../sw.js"))
    into(layout.projectDirectory.dir("src/main/assets/www"))
}
tasks.named("preBuild") { dependsOn(fetchModel, copyGame) }
