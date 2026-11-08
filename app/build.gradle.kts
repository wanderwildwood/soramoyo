import java.util.Properties
import java.util.regex.Pattern

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutlibraries.android)
}

android {
    namespace = "com.wanderwildwood.soramoyo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wanderwildwood.soramoyo"
        // The Kompakt runs Android 12 (API 31); nothing here needs anything newer, or even
        // that: Supernote's tablets run Android 11 (API 30), and it installs there too.
        minSdk = 30
        targetSdk = 31
        versionCode = 69
        versionName = "0.13.7"
    }

    // A real keystore in signing/ signs every build type when it is present, so the
    // very first install is already release-signed and a later update can never hit
    // INSTALL_FAILED_UPDATE_INCOMPATIBLE. It is gitignored, and there is no fallback:
    // a fresh clone builds an unsigned release APK, which will not install anywhere.
    val signingPropertiesFile = rootProject.file("signing/signing.properties")
    val realSigningConfig = if (signingPropertiesFile.isFile) {
        val signingProperties = Properties().apply {
            signingPropertiesFile.inputStream().use(::load)
        }
        signingConfigs.create("real") {
            storeFile = rootProject.file("signing/signing.keystore")
            storePassword = signingProperties.getProperty("STORE_PASSWORD")
            keyAlias = signingProperties.getProperty("KEY_ALIAS")
            keyPassword = signingProperties.getProperty("KEY_PASSWORD")
        }
    } else {
        null
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            realSigningConfig?.let { signingConfig = it }
        }
        getByName("release") {
            // AGP otherwise stamps the git revision of the build into META-INF, which is the
            // one thing that differs between a release built on the build box, from an rsync
            // with no .git, and the one GitHub publishes from a clone. Off, so the two hash
            // the same.
            vcsInfo {
                include = false
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            realSigningConfig?.let { signingConfig = it }
        }
    }

    lint {
        // Sideloaded onto a Kompakt, not going to Google Play, whose API-33 floor this
        // otherwise trips. Targeting the OS the device actually runs is deliberate.
        disable += "ExpiredTargetSdkVersion"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        // The About dialog shows the version it is actually running.
        buildConfig = true
    }

    sourceSets {
        named("main") { kotlin.srcDir("src/main/kotlin") }
        named("test") { kotlin.srcDir("src/test/kotlin") }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.mmd)

    // The radar's tiles and metadata, as kRadar fetches them.
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)

    // The half-hourly check for serious weather alerts, only while that setting is on.
    implementation(libs.work.runtime)
    implementation(libs.unifiedpush.connector)

    testImplementation(libs.junit)
    testImplementation(libs.orgjson)
}

// The Licenses page lists what the build actually packs: this plugin walks each variant's runtime
// dependencies and writes res/raw/aboutlibraries.json (one entry per artifact, with its POM's
// licence). What Gradle cannot see (fonts, data sets, native code, desugaring) and licence texts
// of our own choosing are in config/libraries and config/licenses. Offline: the build fetches
// nothing; the full texts come from res/raw and config/licenses. The app reads the file with
// shared-licences' LicencesJson.kt (no AboutLibraries runtime library).
aboutLibraries {
    offlineMode.set(true)
    collect {
        configPath.set(rootProject.file("config"))
        fetchRemoteLicense.set(false)
        fetchRemoteFunding.set(false)
        includePlatform.set(false)
    }
    export {
        excludeFields.addAll("funding")
    }
    library {
        mergePlatformArtifacts.set(true)
        // Only what the release APK carries: these come in through debugImplementation (ui-tooling)
        // alone; the release runtime classpath has none of them (checked 4 Nov 2026).
        exclusionPatterns.addAll(
            listOf(
                "androidx.compose.ui:ui-tooling", "androidx.compose.ui:ui-tooling-data",
                "androidx.compose.material:material", "androidx.compose.material:material-icons-core",
            ).map { Pattern.compile("^" + Regex.escape(it) + "$") },
        )
    }
}

// The build fails when a bundled library has no license, or a license with no full text to show
// (neither its own text from config/licenses nor a family text in res/raw). Mudita review, 4 Nov 2026.
val licenceRawTexts: List<String> = file("src/main/res/raw").listFiles().orEmpty().map { it.name.lowercase() }
tasks.matching { it.name.startsWith("prepareLibraryDefinitions") }.configureEach {
    val raw = licenceRawTexts
    inputs.property("licenceRawTexts", raw) // so the check reruns when a text is added or removed
    doLast {
        fun family(n: String): String? {
            val l = n.lowercase()
            return when {
                "classpath" in l -> "classpath"; "lgpl" in l || "lesser general" in l -> "lgpl"
                "gpl" in l || "general public licen" in l -> "gpl"; "unicode" in l -> "unicode"; "apache" in l -> "apache"
                "mpl" in l || "mozilla" in l -> "mpl"; "mit" in l.split(Regex("[^a-z]+")) -> "mit"; "bsd" in l -> "bsd"
                "ofl" in l || "open font" in l -> "ofl"; "cc-by" in l || "cc by" in l || "cc0" in l || "public domain" in l -> "none"
                else -> null
            }
        }
        val bad = mutableListOf<String>()
        outputs.files.asFileTree.matching { include("**/aboutlibraries.json") }.forEach { f ->
            @Suppress("UNCHECKED_CAST")
            val root = groovy.json.JsonSlurper().parse(f) as MutableMap<String, Any?>
            val lics = (root["licenses"] as? Map<String, Map<String, Any?>>).orEmpty()
            // A library's own license file (config/licenses, with its copyright line) stands in for
            // the bare family entry its POM names: the plugin merges config entries in, it never
            // takes one out, so a library would otherwise list "MIT" twice, once with no text.
            var changed = false
            (root["libraries"] as? List<MutableMap<String, Any?>>).orEmpty().forEach { lib ->
                val keys = (lib["licenses"] as? List<String>).orEmpty()
                fun fam(k: String) = family((lics[k]?.get("spdxId") ?: lics[k]?.get("name") ?: k).toString())
                fun hasText(k: String) = !(lics[k]?.get("content") as? String).isNullOrBlank()
                val own = keys.filter { hasText(it) }.mapNotNull { fam(it) }.toSet()
                val kept = keys.filterNot { !hasText(it) && fam(it) in own }
                if (kept != keys) { lib["licenses"] = kept; changed = true }
            }
            if (changed) f.writeText(groovy.json.JsonOutput.toJson(root))
            (root["libraries"] as? List<Map<String, Any?>>).orEmpty().forEach { lib ->
                val keys = (lib["licenses"] as? List<String>).orEmpty()
                if (keys.isEmpty()) bad += "${lib["uniqueId"]}: no license"
                keys.forEach { k ->
                    val e = lics[k].orEmpty()
                    if ((e["content"] as? String).isNullOrBlank()) {
                        val fam = family((e["spdxId"] ?: e["name"] ?: k).toString())
                        if (fam == null || (fam != "none" && raw.none { fam in it }))
                            bad += "${lib["uniqueId"]}: no text for ${e["name"] ?: k}"
                    }
                }
            }
        }
        if (bad.isNotEmpty()) throw GradleException("Licenses page: add the text in config/licenses or res/raw: " + bad.distinct().joinToString("; "))
    }
}
