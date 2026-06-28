import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.17.0"
}

group = property("pluginGroup").toString()
version = property("pluginVersion").toString()

val platformVersion = providers.gradleProperty("platformVersion").get()
val signingCertificateChainFilePath = providers.environmentVariable("CERTIFICATE_CHAIN_FILE")
    .orElse(providers.gradleProperty("intellijPlatformSigningCertificateChainFile"))
val signingCertificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
    .orElse(providers.gradleProperty("intellijPlatformSigningCertificateChain"))
val signingPrivateKeyFilePath = providers.environmentVariable("PRIVATE_KEY_FILE")
    .orElse(providers.gradleProperty("intellijPlatformSigningPrivateKeyFile"))
val signingPrivateKey = providers.environmentVariable("PRIVATE_KEY")
    .orElse(providers.gradleProperty("intellijPlatformSigningPrivateKey"))
val signingPassword = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    .orElse(providers.gradleProperty("intellijPlatformSigningPassword"))
val hasSigningSecrets = listOf(
    signingCertificateChainFilePath.orNull ?: signingCertificateChain.orNull,
    signingPrivateKeyFilePath.orNull ?: signingPrivateKey.orNull,
).all { !it.isNullOrBlank() }
val applicationsDir = file("${System.getProperty("user.home")}/Applications")
val localRiderCandidates = applicationsDir.listFiles()
    ?.filter { it.isDirectory && it.name.startsWith("Rider") }
    ?.sortedWith(
        compareByDescending<File> { it.name.contains(platformVersion) }
            .thenByDescending { it.name == "Rider.app" }
    )
    .orEmpty()
val localRider = localRiderCandidates.firstOrNull()

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

val helperProjectFile = layout.projectDirectory.file("tools/BinlogJsonExporter/BinlogJsonExporter.csproj")
val helperPublishDir = layout.buildDirectory.dir("binlog-helper/publish")
val helperResourcesDir = layout.buildDirectory.dir("generated-resources/main/binlog-helper")

sourceSets {
    main {
        resources.srcDir(layout.buildDirectory.dir("generated-resources/main"))
    }
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.3")

    intellijPlatform {
        if (localRider != null) {
            local(localRider.absolutePath)
        } else {
            rider(platformVersion) {
                useInstaller = false
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    if (hasSigningSecrets) {
        signing {
            signingCertificateChainFilePath.orNull?.let { certificateChainFile.set(file(it)) }
                ?: run { certificateChain = signingCertificateChain }
            signingPrivateKeyFilePath.orNull?.let { privateKeyFile.set(file(it)) }
                ?: run { privateKey = signingPrivateKey }
            password = signingPassword
        }
    }

    publishing {
        token = providers.environmentVariable("JETBRAINS_TOKEN")
        channels = listOf("default")
    }
}

val publishBinlogHelper by tasks.registering(Exec::class) {
    inputs.files(fileTree("tools/BinlogJsonExporter") {
        exclude("**/bin/**")
        exclude("**/obj/**")
    })
    outputs.dir(helperPublishDir)

    commandLine(
        "dotnet",
        "publish",
        helperProjectFile.asFile.absolutePath,
        "-c",
        "Release",
        "-o",
        helperPublishDir.get().asFile.absolutePath,
        "--nologo",
    )
}

val stageBinlogHelper by tasks.registering(Sync::class) {
    dependsOn(publishBinlogHelper)
    from(helperPublishDir)
    into(helperResourcesDir)

    doLast {
        val root = helperResourcesDir.get().asFile
        root.mkdirs()

        val entries = root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()

        root.resolve("manifest.txt").writeText(entries.joinToString(separator = "\n", postfix = "\n"))
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

tasks.processResources {
    dependsOn(stageBinlogHelper)
}

tasks.named("buildSearchableOptions") {
    enabled = false
}

tasks.named("verifyPluginSignature") {
    dependsOn(tasks.named("signPlugin"))
}

tasks.clean {
    delete(helperPublishDir, helperResourcesDir)
}

tasks.wrapper {
    gradleVersion = "9.4.1"
}
