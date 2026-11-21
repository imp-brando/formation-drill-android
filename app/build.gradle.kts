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
        versionCode = 2
        versionName = "0.1.1"
        // Where the game is loaded from (edits go live without an app update)
        buildConfigField("String", "GAME_URL", "\"https://imp-brando.github.io/formation-drill-html/\"")
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
/* Bundled fallback copy of the game (for a first launch with no signal): the current build from the
   HTML repo's Pages site, fetched at build time. Override with -PgameDir=/path/to/formation-drill-html. */
val gameUrl = "https://imp-brando.github.io/formation-drill-html/"
val wwwDir = layout.projectDirectory.dir("src/main/assets/www")
val copyGame by tasks.registering {
    outputs.dir(wwwDir)
    outputs.upToDateWhen { false }
    doLast {
        val out = wwwDir.asFile; out.mkdirs()
        val local = project.findProperty("gameDir")?.toString()
        for (name in listOf("index.html", "sw.js")) {
            val dst = File(out, name)
            if (local != null) File(local, name).copyTo(dst, overwrite = true)
            else URL(gameUrl + name).openStream().use { inp -> dst.outputStream().use { inp.copyTo(it) } }
        }
    }
}
tasks.named("preBuild") { dependsOn(fetchModel, copyGame) }
