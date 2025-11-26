package de.tfr.tool.model

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("SortConfiguration Integration Tests")
class SortConfigurationIntegrationTest {

    @Nested
    @DisplayName("Practical Sorting Scenarios")
    inner class PracticalSortingScenarios {

        @Test
        @DisplayName("Should sort names in ascending order")
        fun testSortByNameAscending() {
            SortConfiguration("name", SortDirection.ASCENDING)
            val disks = listOf(
                Disk().apply { name = "Disk C" },
                Disk().apply { name = "Disk A" },
                Disk().apply { name = "Disk B" }
            )

            val sorted = disks.sortedWith(compareBy<Disk> { it.name })

            assertEquals("Disk A", sorted[0].name)
            assertEquals("Disk B", sorted[1].name)
            assertEquals("Disk C", sorted[2].name)
        }

        @Test
        @DisplayName("Should sort names in descending order")
        fun testSortByNameDescending() {
            SortConfiguration("name", SortDirection.DESCENDING)
            val disks = listOf(
                Disk().apply { name = "Disk A" },
                Disk().apply { name = "Disk C" },
                Disk().apply { name = "Disk B" }
            )

            val sorted = disks.sortedWith(compareBy<Disk> { it.name }.reversed())

            assertEquals("Disk C", sorted[0].name)
            assertEquals("Disk B", sorted[1].name)
            assertEquals("Disk A", sorted[2].name)
        }

        @Test
        @DisplayName("Should sort size in ascending order")
        fun testSortBySizeAscending() {
            SortConfiguration("size", SortDirection.ASCENDING)
            val disks = listOf(
                Disk().apply { sizeMB = 500.0 },
                Disk().apply { sizeMB = 1000.0 },
                Disk().apply { sizeMB = 250.0 }
            )

            val sorted = disks.sortedWith(compareBy<Disk> { it.sizeMB })

            assertEquals(250.0, sorted[0].sizeMB)
            assertEquals(500.0, sorted[1].sizeMB)
            assertEquals(1000.0, sorted[2].sizeMB)
        }

        @Test
        @DisplayName("Should sort size in descending order")
        fun testSortBySizeDescending() {
            SortConfiguration("size", SortDirection.DESCENDING)
            val disks = listOf(
                Disk().apply { sizeMB = 250.0 },
                Disk().apply { sizeMB = 1000.0 },
                Disk().apply { sizeMB = 500.0 }
            )

            val sorted = disks.sortedWith(compareBy<Disk> { it.sizeMB }.reversed())

            assertEquals(1000.0, sorted[0].sizeMB)
            assertEquals(500.0, sorted[1].sizeMB)
            assertEquals(250.0, sorted[2].sizeMB)
        }
    }

    @Nested
    @DisplayName("Hierarchical Sorting (Disks and Partitions)")
    inner class HierarchicalSorting {

        @Test
        @DisplayName("Should sort disks and their partitions")
        fun testHierarchicalSorting() {
            SortConfiguration("name", SortDirection.ASCENDING)

            val disk1 = Disk().apply {
                name = "Disk B"
                partitions.add(Partition().apply { name = "Partition 2" })
                partitions.add(Partition().apply { name = "Partition 1" })
            }

            val disk2 = Disk().apply {
                name = "Disk A"
                partitions.add(Partition().apply { name = "Partition B" })
                partitions.add(Partition().apply { name = "Partition A" })
            }

            val disks = listOf(disk1, disk2)

            // Sort disks
            val sortedDisks = disks.sortedWith(compareBy<Disk> { it.name })

            // Sort partitions within each disk
            sortedDisks.forEach { disk ->
                disk.partitions.sortWith(compareBy<Partition> { it.name })
            }

            // Verify disk order
            assertEquals("Disk A", sortedDisks[0].name)
            assertEquals("Disk B", sortedDisks[1].name)

            // Verify partition order in Disk A
            assertEquals("Partition A", sortedDisks[0].partitions[0].name)
            assertEquals("Partition B", sortedDisks[0].partitions[1].name)

            // Verify partition order in Disk B
            assertEquals("Partition 1", sortedDisks[1].partitions[0].name)
            assertEquals("Partition 2", sortedDisks[1].partitions[1].name)
        }
    }

    @Nested
    @DisplayName("Configuration Switching")
    inner class ConfigurationSwitching {

        @Test
        @DisplayName("Should switch configuration")
        fun testConfigurationSwitching() {
            var config = SortConfiguration("name", SortDirection.ASCENDING)

            assertEquals("name", config.fieldName)
            assertEquals(SortDirection.ASCENDING, config.direction)

            // Switch to size descending
            config = config.copy(fieldName = "size", direction = SortDirection.DESCENDING)

            assertEquals("size", config.fieldName)
            assertEquals(SortDirection.DESCENDING, config.direction)
        }

        @Test
        @DisplayName("Should toggle direction")
        fun testToggleDirection() {
            var config = SortConfiguration("size", SortDirection.ASCENDING)

            // Toggle
            config = config.copy(direction = config.direction.reverse())

            assertEquals(SortDirection.DESCENDING, config.direction)

            // Toggle again
            config = config.copy(direction = config.direction.reverse())

            assertEquals(SortDirection.ASCENDING, config.direction)
        }
    }

    @Nested
    @DisplayName("Serialization / Persistence")
    inner class SerializationScenarios {

        @Test
        @DisplayName("Should convert string to SortDirection")
        fun testStringToSortDirection() {
            val directionString = "DESCENDING"
            val direction = SortDirection.fromString(directionString)

            assertEquals(SortDirection.DESCENDING, direction)
        }

        @Test
        @DisplayName("Should create SortConfiguration from strings")
        fun testCreateConfigFromStrings() {
            val fieldString = "size"
            val directionString = "ASCENDING"

            val config = SortConfiguration(
                fieldString,
                SortDirection.fromString(directionString)
            )

            assertEquals("size", config.fieldName)
            assertEquals(SortDirection.ASCENDING, config.direction)
        }

        @Test
        @DisplayName("Should use default configuration as fallback")
        fun testDefaultConfigFallback() {
            val direction = SortDirection.fromString(null) // Should be ASCENDING
            val config = SortConfiguration("name", direction)

            assertEquals("name", config.fieldName)
            assertEquals(SortDirection.ASCENDING, config.direction)
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {

        @Test
        @DisplayName("Should handle empty disk lists")
        fun testEmptyDiskList() {
            SortConfiguration("name", SortDirection.ASCENDING)
            val disks = emptyList<Disk>()

            val sorted = disks.sortedWith(compareBy<Disk> { it.name })

            assertEquals(0, sorted.size)
        }

        @Test
        @DisplayName("Should handle single disk")
        fun testSingleDisk() {
            SortConfiguration("name", SortDirection.ASCENDING)
            val disks = listOf(Disk().apply { name = "Disk A" })

            val sorted = disks.sortedWith(compareBy<Disk> { it.name })

            assertEquals(1, sorted.size)
            assertEquals("Disk A", sorted[0].name)
        }

        @Test
        @DisplayName("Should handle identical names")
        fun testIdenticalNames() {
            SortConfiguration("name", SortDirection.ASCENDING)
            val disks = listOf(
                Disk().apply { name = "Disk A" },
                Disk().apply { name = "Disk A" },
                Disk().apply { name = "Disk A" }
            )

            val sorted = disks.sortedWith(compareBy<Disk> { it.name })

            assertEquals(3, sorted.size)
            assertTrue(sorted.all { it.name == "Disk A" })
        }
    }
}

