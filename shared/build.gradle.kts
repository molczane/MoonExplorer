import java.net.URI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "org.jetbrains.moonexplorer.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        androidResources { enable = true }

        withHostTest {}
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
        }
        androidMain.dependencies {
            implementation(libs.filament.android)
            implementation(libs.filament.utils.android)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

// --- Filament material toolchain (00-renderer-spike T003 + T004) ---
// matc is downloaded on demand into tools/matc/<version>/<os>/ (gitignored)
// rather than vendored. See ai-docs/specs/00-renderer-spike/tasks.md T003.
val filamentVersion = libs.versions.filament.get()
val matcOsName = when {
    System.getProperty("os.name").lowercase().contains("mac") -> "mac"
    System.getProperty("os.name").lowercase().contains("linux") -> "linux"
    else -> error("Filament matc is published for Mac and Linux only; host OS not supported.")
}
val matcCacheDir = rootProject.layout.projectDirectory.dir("tools/matc/$filamentVersion/$matcOsName")
val matcBinaryFile = matcCacheDir.file("matc").asFile

val downloadFilamentTools by tasks.registering {
    description = "Download the Filament tools tarball and extract matc into tools/matc/<version>/<os>/."
    group = "filament"
    val targetBin = matcBinaryFile
    val ver = filamentVersion
    val os = matcOsName
    outputs.file(targetBin)
    onlyIf { !targetBin.exists() }
    doLast {
        val parent = targetBin.parentFile
        parent.mkdirs()
        val tarball = parent.resolve("filament-tools.tgz")
        val url = "https://github.com/google/filament/releases/download/v$ver/filament-v$ver-$os.tgz"
        logger.lifecycle("Downloading $url")
        URI(url).toURL().openStream().use { input ->
            tarball.outputStream().use { input.copyTo(it) }
        }
        val tarProc = ProcessBuilder(
            "tar", "-xzf", tarball.absolutePath,
            "-C", parent.absolutePath,
            "filament/bin/matc"
        ).inheritIO().start()
        check(tarProc.waitFor() == 0) { "tar extraction failed for $tarball" }
        val extracted = parent.resolve("filament/bin/matc")
        check(extracted.exists()) { "matc not found at filament/bin/matc inside tarball" }
        extracted.copyTo(targetBin, overwrite = true)
        targetBin.setExecutable(true)
        parent.resolve("filament").deleteRecursively()
        tarball.delete()
        logger.lifecycle("matc installed at $targetBin")
    }
}

val compileMaterials by tasks.registering(Exec::class) {
    description = "Compile Filament .mat materials to .filamat (-a all -p mobile)."
    group = "filament"
    dependsOn(downloadFilamentTools)

    val srcFile = layout.projectDirectory
        .file("src/commonMain/composeResources/files/materials/moon.mat").asFile
    val outFile = layout.buildDirectory
        .file("generated/filamat/moon.filamat").get().asFile
    val resourceCopy = layout.projectDirectory
        .file("src/commonMain/composeResources/files/materials/moon.filamat").asFile
    val matcExec = matcBinaryFile

    // No-op until Phase 3 (T030) creates moon.mat.
    onlyIf { srcFile.exists() }
    outputs.file(outFile)

    doFirst { outFile.parentFile.mkdirs() }
    commandLine = listOf(
        matcExec.absolutePath,
        "-a", "all",
        "-p", "mobile",
        "-o", outFile.absolutePath,
        srcFile.absolutePath,
    )
    doLast { outFile.copyTo(resourceCopy, overwrite = true) }
}

// Wire compileMaterials in front of Compose Resources packaging.
// The onlyIf-guard above keeps it a no-op until moon.mat exists (Phase 3).
tasks.matching {
    it.name.startsWith("processAndroidMainResources") ||
        it.name.startsWith("syncComposeResourcesForIos") ||
        it.name == "generateComposeResClass"
}.configureEach {
    dependsOn(compileMaterials)
}
