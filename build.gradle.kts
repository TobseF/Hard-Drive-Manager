plugins {
    kotlin("jvm") version "2.2.20"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "de.tfr.tool"
version = "1.0-SNAPSHOT"

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

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(24)
}

application {
    mainClass.set("de.tfr.tool.HardDriveManagerAppKt")
    // Enable native access for JavaFX graphics module and ALL-UNNAMED (e.g., sqlite-jdbc)
    // to suppress JDK 22+/24 restricted native access warnings at runtime.
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=javafx.graphics",
        "--enable-native-access=ALL-UNNAMED",
    )
}

javafx {
    version = "23.0.1"
    modules = listOf("javafx.controls", "javafx.graphics", "javafx.swing")
}