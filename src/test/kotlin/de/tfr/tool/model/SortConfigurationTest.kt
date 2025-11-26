package de.tfr.tool.model

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@DisplayName("SortConfiguration Tests")
class SortConfigurationTest {

    @Nested
    @DisplayName("Constructor and Default Values")
    inner class ConstructorTests {

        @Test
        @DisplayName("Should initialize with default values")
        fun testDefaultConstructor() {
            val config = SortConfiguration()

            assertEquals("name", config.fieldName)
            assertEquals(SortDirection.ASCENDING, config.direction)
        }

        @Test
        @DisplayName("Should initialize with custom values")
        fun testCustomConstructor() {
            val config = SortConfiguration("size", SortDirection.DESCENDING)

            assertEquals("size", config.fieldName)
            assertEquals(SortDirection.DESCENDING, config.direction)
        }

        @Test
        @DisplayName("Should accept different field names")
        fun testDifferentFieldNames() {
            val fieldNames = listOf("name", "type", "size", "used", "free", "letter", "tags")

            fieldNames.forEach { fieldName ->
                val config = SortConfiguration(fieldName, SortDirection.ASCENDING)
                assertEquals(fieldName, config.fieldName)
            }
        }
    }

    @Nested
    @DisplayName("Copy Function (Data Class)")
    inner class CopyFunctionTests {

        @Test
        @DisplayName("Should copy all properties")
        fun testCopyAllProperties() {
            val original = SortConfiguration("size", SortDirection.DESCENDING)
            val copy = original.copy()

            assertEquals(original.fieldName, copy.fieldName)
            assertEquals(original.direction, copy.direction)
            assertEquals(original, copy)
        }

        @Test
        @DisplayName("Should change only fieldName")
        fun testCopyChangeFieldName() {
            val original = SortConfiguration("size", SortDirection.DESCENDING)
            val modified = original.copy(fieldName = "used")

            assertEquals("used", modified.fieldName)
            assertEquals(SortDirection.DESCENDING, modified.direction)
            assertNotEquals(original, modified)
        }

        @Test
        @DisplayName("Should change only direction")
        fun testCopyChangeDirection() {
            val original = SortConfiguration("size", SortDirection.DESCENDING)
            val modified = original.copy(direction = SortDirection.ASCENDING)

            assertEquals("size", modified.fieldName)
            assertEquals(SortDirection.ASCENDING, modified.direction)
            assertNotEquals(original, modified)
        }

        @Test
        @DisplayName("Should change both values")
        fun testCopyChangeBoth() {
            val original = SortConfiguration("size", SortDirection.DESCENDING)
            val modified = original.copy(fieldName = "used", direction = SortDirection.ASCENDING)

            assertEquals("used", modified.fieldName)
            assertEquals(SortDirection.ASCENDING, modified.direction)
            assertNotEquals(original, modified)
        }
    }

    @Nested
    @DisplayName("Equality and Hashing")
    inner class EqualityTests {

        @Test
        @DisplayName("Should be equal when field name and direction are same")
        fun testEquality() {
            val config1 = SortConfiguration("size", SortDirection.ASCENDING)
            val config2 = SortConfiguration("size", SortDirection.ASCENDING)

            assertEquals(config1, config2)
            assertEquals(config1.hashCode(), config2.hashCode())
        }

        @Test
        @DisplayName("Should not be equal when field name differs")
        fun testInequalityDifferentField() {
            val config1 = SortConfiguration("size", SortDirection.ASCENDING)
            val config2 = SortConfiguration("used", SortDirection.ASCENDING)

            assertNotEquals(config1, config2)
        }

        @Test
        @DisplayName("Should not be equal when direction differs")
        fun testInequalityDifferentDirection() {
            val config1 = SortConfiguration("size", SortDirection.ASCENDING)
            val config2 = SortConfiguration("size", SortDirection.DESCENDING)

            assertNotEquals(config1, config2)
        }
    }

    @Nested
    @DisplayName("ToString")
    inner class ToStringTests {

        @Test
        @DisplayName("Should provide meaningful string representation")
        fun testToString() {
            val config = SortConfiguration("size", SortDirection.ASCENDING)
            val string = config.toString()

            assertTrue(string.contains("size"))
            assertTrue(string.contains("ASCENDING"))
        }
    }

    @Nested
    @DisplayName("Parameterized Tests")
    inner class ParameterizedTests {

        @ParameterizedTest
        @ValueSource(strings = ["name", "type", "size", "used", "free", "letter", "tags"])
        @DisplayName("Should work with various field names")
        fun testWithVariousFields(fieldName: String) {
            val config = SortConfiguration(fieldName, SortDirection.ASCENDING)

            assertEquals(fieldName, config.fieldName)
            assertEquals(SortDirection.ASCENDING, config.direction)
        }

        @ParameterizedTest
        @CsvSource(
            "name, ASCENDING",
            "size, DESCENDING",
            "used, ASCENDING",
            "free, DESCENDING",
            "type, ASCENDING"
        )
        @DisplayName("Should work with various field and direction combinations")
        fun testWithVariousCombinations(fieldName: String, direction: String) {
            val sortDirection = SortDirection.valueOf(direction)
            val config = SortConfiguration(fieldName, sortDirection)

            assertEquals(fieldName, config.fieldName)
            assertEquals(sortDirection, config.direction)
        }
    }
}

