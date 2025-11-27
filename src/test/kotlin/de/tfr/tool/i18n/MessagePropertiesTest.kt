package de.tfr.tool.i18n

import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessagePropertiesTest {

    @Test
    fun testAllMessagePropertiesExist() {
        val enProperties = loadProperties("messages_en.properties")
        val deProperties = loadProperties("messages_de.properties")

        val enKeys = enProperties.stringPropertyNames().sorted()
        val deKeys = deProperties.stringPropertyNames().sorted()

        println("English properties: ${enKeys.size}")
        println("German properties: ${deKeys.size}")

        // Check that all English keys have a German translation
        val missingInDE = enKeys.filter { it !in deKeys }

        if (missingInDE.isNotEmpty()) {
            println("\n=== Missing German translations for: ===")
            missingInDE.forEach { key ->
                println("$key = ${enProperties.getProperty(key)}")
            }
        }

        // Check that all German keys are also in English (no extra keys)
        val extraInDE = deKeys.filter { it !in enKeys }
        if (extraInDE.isNotEmpty()) {
            println("\n=== Extra German keys (not in English): ===")
            extraInDE.forEach { key ->
                println("$key = ${deProperties.getProperty(key)}")
            }
        }

        assertTrue(missingInDE.isEmpty(), "Missing German translations: $missingInDE")
        assertEquals(enKeys, deKeys, "German and English keys should be identical")
    }

    @Test
    fun testNoEmptyTranslations() {
        val deProperties = loadProperties("messages_de.properties")
        val emptyKeys = deProperties.stringPropertyNames().filter {
            deProperties.getProperty(it).isNullOrBlank()
        }

        if (emptyKeys.isNotEmpty()) {
            println("\n=== Empty German translations: ===")
            emptyKeys.forEach { println(it) }
        }

        assertTrue(emptyKeys.isEmpty(), "Found empty translations: $emptyKeys")
    }

    @Test
    fun testNoCorruptedUmlauts() {
        val deProperties = loadProperties("messages_de.properties")

        // Valid German umlauts in UTF-8:
        // ä (U+00E4 = E4), ö (U+00F6 = F6), ü (U+00FC = FC)
        // Ä (U+00C4 = C4), Ö (U+00D6 = D6), Ü (U+00DC = DC)
        // ß (U+00DF = DF)

        val corruptedKeys = mutableListOf<Pair<String, String>>()

        for (key in deProperties.stringPropertyNames()) {
            val value = deProperties.getProperty(key)
            var isCorrupted = false

            for (char in value) {
                val code = char.code

                // Check for replacement character (U+FFFD)
                if (char == '\ufffd') {
                    isCorrupted = true
                    break
                }

                // Check for suspicious byte combinations
                // Corrupted UTF-8 often results in:
                // - Multiple ?
                // - Single bytes > 127 that don't fit valid UTF-8
                // - Double character codes at suspicious positions
                if (code == 0x3F) {  // ?
                    // ? could indicate corruption, especially if multiple in sequence
                    val questionCount = value.count { it == '?' }
                    if (questionCount > 1) {
                        isCorrupted = true
                        break
                    }
                }

                // Accept only:
                // - ASCII (0-127)
                // - Valid German/European characters (U+00C0 to U+017F)
                // - Valid special characters like {, }, [, ], etc.
                if (code > 127 && (code < 0xC0 || code > 0x17F)) {
                    // Could be corrupted UTF-8
                    isCorrupted = true
                    break
                }
            }

            if (isCorrupted) {
                corruptedKeys.add(key to value)
            }
        }

        if (corruptedKeys.isNotEmpty()) {
            println("\n=== Corrupted/Invalid Characters Found: ===")
            corruptedKeys.forEach { (key, value) ->
                println("$key = $value")
                value.forEachIndexed { index, char ->
                    if (char.code > 127) {
                        println("  Position $index: U+${char.code.toString(16).padStart(4, '0').uppercase()}")
                    }
                }
            }
        }

        assertTrue(corruptedKeys.isEmpty(), "Found corrupted values in ${corruptedKeys.size} keys")
    }

    private fun loadProperties(filename: String): Properties {
        val properties = Properties()
        val inputStream = javaClass.classLoader.getResourceAsStream("i18n/$filename")
            ?: throw IllegalArgumentException("Cannot find $filename in classpath")
        properties.load(inputStream)
        inputStream.close()
        return properties
    }
}

