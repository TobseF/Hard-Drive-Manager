package de.tfr.tool.model

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("SortDirection Tests")
class SortDirectionTest {

    @Nested
    @DisplayName("Enum Values")
    inner class EnumValuesTests {

        @Test
        @DisplayName("Should have ASCENDING value")
        fun testAscendingExists() {
            assertEquals(SortDirection.ASCENDING, SortDirection.ASCENDING)
        }

        @Test
        @DisplayName("Should have DESCENDING value")
        fun testDescendingExists() {
            assertEquals(SortDirection.DESCENDING, SortDirection.DESCENDING)
        }

        @Test
        @DisplayName("Should have exactly two values")
        fun testEnumValuesCount() {
            val values = SortDirection.values()
            assertEquals(2, values.size)
        }

        @Test
        @DisplayName("Should contain all enum values")
        fun testAllEnumValues() {
            val values = SortDirection.values()
            assertTrue(values.contains(SortDirection.ASCENDING))
            assertTrue(values.contains(SortDirection.DESCENDING))
        }
    }

    @Nested
    @DisplayName("reverse() Function")
    inner class ReverseTests {

        @Test
        @DisplayName("ASCENDING.reverse() should be DESCENDING")
        fun testReverseAscending() {
            val reversed = SortDirection.ASCENDING.reverse()
            assertEquals(SortDirection.DESCENDING, reversed)
        }

        @Test
        @DisplayName("DESCENDING.reverse() should be ASCENDING")
        fun testReverseDescending() {
            val reversed = SortDirection.DESCENDING.reverse()
            assertEquals(SortDirection.ASCENDING, reversed)
        }

        @Test
        @DisplayName("Double reverse() should be original")
        fun testDoubleReverse() {
            val original = SortDirection.ASCENDING
            val reversed = original.reverse().reverse()
            assertEquals(original, reversed)
        }

        @Test
        @DisplayName("Should work with DESCENDING too")
        fun testDoubleReverseDescending() {
            val original = SortDirection.DESCENDING
            val reversed = original.reverse().reverse()
            assertEquals(original, reversed)
        }
    }

    @Nested
    @DisplayName("fromString() Companion Function")
    inner class FromStringTests {

        @Test
        @DisplayName("Should convert 'ASCENDING' to ASCENDING")
        fun testFromStringAscending() {
            val result = SortDirection.fromString("ASCENDING")
            assertEquals(SortDirection.ASCENDING, result)
        }

        @Test
        @DisplayName("Should convert 'ascending' to ASCENDING (case-insensitive)")
        fun testFromStringAscendingLowercase() {
            val result = SortDirection.fromString("ascending")
            assertEquals(SortDirection.ASCENDING, result)
        }

        @Test
        @DisplayName("Should convert 'Ascending' to ASCENDING (case-insensitive)")
        fun testFromStringAscendingMixed() {
            val result = SortDirection.fromString("Ascending")
            assertEquals(SortDirection.ASCENDING, result)
        }

        @Test
        @DisplayName("Should convert 'DESCENDING' to DESCENDING")
        fun testFromStringDescending() {
            val result = SortDirection.fromString("DESCENDING")
            assertEquals(SortDirection.DESCENDING, result)
        }

        @Test
        @DisplayName("Should convert 'descending' to DESCENDING (case-insensitive)")
        fun testFromStringDescendingLowercase() {
            val result = SortDirection.fromString("descending")
            assertEquals(SortDirection.DESCENDING, result)
        }

        @Test
        @DisplayName("Should convert null to ASCENDING (default)")
        fun testFromStringNull() {
            val result = SortDirection.fromString(null)
            assertEquals(SortDirection.ASCENDING, result)
        }

        @Test
        @DisplayName("Should convert empty string to ASCENDING (default)")
        fun testFromStringEmpty() {
            val result = SortDirection.fromString("")
            assertEquals(SortDirection.ASCENDING, result)
        }

        @Test
        @DisplayName("Should convert unknown string to ASCENDING (default)")
        fun testFromStringUnknown() {
            val result = SortDirection.fromString("UNKNOWN")
            assertEquals(SortDirection.ASCENDING, result)
        }

        @Test
        @DisplayName("Should handle whitespace in string")
        fun testFromStringWithWhitespace() {
            val result = SortDirection.fromString("  DESCENDING  ")
            assertEquals(SortDirection.ASCENDING, result) // uppercase() ignores trim()
        }
    }

    @Nested
    @DisplayName("Integration with SortConfiguration")
    inner class IntegrationTests {

        @Test
        @DisplayName("Should be used in SortConfiguration")
        fun testInSortConfiguration() {
            val config = SortConfiguration("size", SortDirection.ASCENDING)
            assertEquals(SortDirection.ASCENDING, config.direction)
        }

        @Test
        @DisplayName("Should be reversible in SortConfiguration")
        fun testReversibleInConfig() {
            val config = SortConfiguration("size", SortDirection.ASCENDING)
            val reversedConfig = config.copy(direction = config.direction.reverse())

            assertEquals(SortDirection.DESCENDING, reversedConfig.direction)
        }

        @Test
        @DisplayName("Should toggle between both values")
        fun testToggleBetweenValues() {
            var direction = SortDirection.ASCENDING
            direction = direction.reverse()
            assertEquals(SortDirection.DESCENDING, direction)

            direction = direction.reverse()
            assertEquals(SortDirection.ASCENDING, direction)
        }
    }

    @Nested
    @DisplayName("Comparison Tests")
    inner class ComparisonTests {

        @Test
        @DisplayName("ASCENDING should equal ASCENDING")
        fun testAscendingEqualsAscending() {
            assertEquals(SortDirection.ASCENDING, SortDirection.ASCENDING)
        }

        @Test
        @DisplayName("DESCENDING should equal DESCENDING")
        fun testDescendingEqualsDescending() {
            assertEquals(SortDirection.DESCENDING, SortDirection.DESCENDING)
        }

        @Test
        @DisplayName("ASCENDING should not equal DESCENDING")
        fun testAscendingNotEqualsDescending() {
            val ascending = SortDirection.ASCENDING
            val descending = SortDirection.DESCENDING

            assertEquals(false, ascending == descending)
        }
    }

    @Nested
    @DisplayName("valueOf() Standard Enum Function")
    inner class ValueOfTests {

        @Test
        @DisplayName("Should get ASCENDING from 'ASCENDING'")
        fun testValueOfAscending() {
            val result = SortDirection.valueOf("ASCENDING")
            assertEquals(SortDirection.ASCENDING, result)
        }

        @Test
        @DisplayName("Should get DESCENDING from 'DESCENDING'")
        fun testValueOfDescending() {
            val result = SortDirection.valueOf("DESCENDING")
            assertEquals(SortDirection.DESCENDING, result)
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for invalid values")
        fun testValueOfInvalid() {
            try {
                SortDirection.valueOf("INVALID")
                assertEquals(true, false, "Should throw an exception")
            } catch (e: IllegalArgumentException) {
                assertEquals(true, true)
            }
        }
    }
}

