import hanja.build.PackTsv
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/** src/main/assets/NAME.tsv that go into the APK as NAME.pk (PackTsv in buildSrc, read by PackedTsv.kt). */
val packedDictionaries = listOf("parts", "words", "kanji", "origin", "usage", "expand", "similar", "radkanji", "extra")

/** Packs the dictionaries into [outputDir]: 25 MB of sorted TSV become about 10 MB the app still looks keys up in. */
abstract class PackAssetsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun pack() {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        for (source in sources.files.sortedBy { it.name }) {
            PackTsv.pack(source, File(dir, source.name.removeSuffix(".tsv") + ".pk"))
        }
    }
}

android {
    namespace = "com.ggumtak.hanja"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ggumtak.hanja"
        minSdk = 26
        targetSdk = 34
        // CI passes its run number, so every build is a higher version than the one installed.
        val buildNumber = System.getenv("BUILD_NUMBER")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    // Release signing comes from the environment (CI: the repository secrets, or the key kept in the Actions
    // cache); keys never live in the repo. Every CI build must use the same key, or the APK cannot be installed
    // over the previous one.
    val keystorePath = System.getenv("SIGNING_KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null && file(keystorePath).exists()) {
            create("personal") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }
    val releaseSigning = signingConfigs.findByName("personal") ?: signingConfigs.getByName("debug")

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = releaseSigning
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        // The packed dictionaries (packAssets below) are memory-mapped straight from the APK (WordStore.mapAsset);
        // they are deflated inside already.
        noCompress += "pk"
        // Their sources stay out of the APK: the default patterns, then the dictionaries that are packed.
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~:" +
            packedDictionaries.joinToString(":") { "!$it.tsv" }
    }

    packaging {
        resources.excludes += listOf("META-INF/*.kotlin_module", "kotlin/**")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

androidComponents {
    onVariants { variant ->
        val pack = tasks.register<PackAssetsTask>("pack${variant.name.replaceFirstChar { it.uppercase() }}Dictionaries") {
            sources.from(packedDictionaries.map { "src/main/assets/$it.tsv" })
        }
        variant.sources.assets?.addGeneratedSourceDirectory(pack, PackAssetsTask::outputDir)
    }
}

// The unit tests check the packed dictionaries line by line against their sources (PackedTsvTest).
val packDictionariesForTests = tasks.register<PackAssetsTask>("packDictionariesForTests") {
    sources.from(packedDictionaries.map { "src/main/assets/$it.tsv" })
    outputDir.set(layout.buildDirectory.dir("packedForTests"))
}
tasks.withType<Test>().configureEach {
    inputs.files(packDictionariesForTests)
    systemProperty("packedAssets", layout.buildDirectory.dir("packedForTests").get().asFile.absolutePath)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
