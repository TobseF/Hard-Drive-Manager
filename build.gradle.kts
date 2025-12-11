plugins {
    kotlin("jvm") version "2.3.0-RC3"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("org.beryx.runtime") version "1.13.1"
}

group = "de.tfr.tool"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    // SQLite JDBC driver
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("com.github.oshi:oshi-core:6.9.1")

    // Kotlin logging facade + simple SLF4J backend
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    // Title bar theming
    implementation("net.yetihafen:javafx-customcaption:1.0.1")
    // AtlantaFX theme library
    implementation("io.github.mkpaz:atlantafx-base:2.0.1")
    // Icons
    implementation("org.kordamp.ikonli:ikonli-javafx:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-feather-pack:12.4.0")
    implementation("org.kordamp.ikonli:ikonli-materialdesign2-pack:12.4.0")

    // Test dependencies
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testImplementation("org.testfx:testfx-core:4.0.18")
    testImplementation("org.testfx:testfx-junit5:4.0.18")
    testImplementation("org.testfx:openjfx-monocle:21.0.2")
}

tasks.test {
    useJUnitPlatform()
    // Headless TestFX configuration so UI tests run in CI (Monocle/Glass)
    systemProperty("java.awt.headless", "true")
    systemProperty("testfx.robot", "glass")
    systemProperty("testfx.headless", "true")
    systemProperty("glass.platform", "Monocle")
    systemProperty("monocle.platform", "Headless")
    systemProperty("prism.order", "sw")
    jvmArgs(
        "--add-opens", "javafx.graphics/com.sun.glass.ui=ALL-UNNAMED",
        "--add-opens", "javafx.graphics/com.sun.javafx.util=ALL-UNNAMED",
        "--add-opens", "javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED",
        "--add-opens", "javafx.graphics/com.sun.glass.ui.monocle=ALL-UNNAMED",
        "--add-opens", "javafx.graphics/com.sun.prism=ALL-UNNAMED",
        "--add-exports", "javafx.graphics/com.sun.glass.ui=ALL-UNNAMED",
        "--add-exports", "javafx.graphics/com.sun.javafx.application=ALL-UNNAMED"
    )
}

kotlin {
    jvmToolchain(23)
}

application {
    mainClass.set("de.tfr.tool.HardDriveManagerAppKt")
    // Enable native access for JavaFX graphics module and ALL-UNNAMED (e.g., sqlite-jdbc)
    // to suppress JDK 22+/24 restricted native access warnings at runtime.
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=javafx.graphics",
        "--enable-native-access=ALL-UNNAMED",
        "-Dapp.version=$version" // Added system property for app version
    )
}

val fxModules = listOf("javafx.controls", "javafx.graphics", "javafx.swing")
val runtimeModules =
    (listOf("java.base", "java.sql", "java.desktop", "java.logging", "jdk.unsupported") + fxModules).toMutableList()

javafx {
    version = "25.0.1"
    modules = fxModules
}

// Define installer type based on current OS
val osName = System.getProperty("os.name").lowercase()
val isWindows = osName.contains("win")
val isMac = osName.contains("mac")

runtime {
    modules.set(runtimeModules)
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))

    launcher {
        noConsole = true
        jvmArgs = application.applicationDefaultJvmArgs.toMutableList()
    }

    jpackage {
        imageName = "Hard Drive Manager"
        appVersion = version.toString()
        val options = listOf(
            "--vendor",
            "TobseF",
            "--description",
            "Inventory explore, and manage your storage devices like a pro!")
        if (isWindows) {
            imageOptions = listOf("--icon", "src/main/resources/icon.ico")
            installerType = "msi"
            installerOptions = listOf(
                "--win-menu",
                "--win-shortcut"
            ) + (options).toMutableList()
        } else if (isMac) {
            imageOptions = listOf("--icon", "src/main/resources/icon.icns")
            installerType = "dmg"
            installerOptions = options
        }
    }
}

tasks.register("packageInstaller") {
    dependsOn(tasks.named("jpackage"))
    group = "distribution"
    description = "Creates a platform installer with embedded JRE for Windows (MSI) or macOS (DMG)."
}
