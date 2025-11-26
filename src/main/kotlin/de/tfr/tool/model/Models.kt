package de.tfr.tool.model

import de.tfr.tool.persist.Settings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleStringProperty

enum class DiskType { SSD, HD }
enum class PartitionType { Partition, EncryptedContainer }
enum class DisplayUnit {
    TB, GB, MB;

    companion object {
        fun fromString(name: String?): DisplayUnit = when (name?.uppercase()) {
            "MB" -> MB
            "GB" -> GB
            else -> TB
        }
    }
}

enum class SortDirection {
    ASCENDING, DESCENDING;

    companion object {
        fun fromString(name: String?): SortDirection = when (name?.uppercase()) {
            "DESCENDING" -> DESCENDING
            else -> ASCENDING
        }
    }

    fun reverse(): SortDirection = if (this == ASCENDING) DESCENDING else ASCENDING
}

data class SortConfiguration(
    val fieldName: String = "name",
    val direction: SortDirection = SortDirection.ASCENDING
)

data class Partition(
    val idProp: SimpleLongProperty = SimpleLongProperty(0L),
    val diskIdProp: SimpleLongProperty = SimpleLongProperty(0L),
    val nameProp: SimpleStringProperty = SimpleStringProperty(""),
    val letterProp: SimpleStringProperty = SimpleStringProperty(""),
    val typeProp: SimpleStringProperty = SimpleStringProperty(PartitionType.Partition.name),
    val sizeMBProp: SimpleDoubleProperty = SimpleDoubleProperty(0.0),
    val usedMBProp: SimpleDoubleProperty = SimpleDoubleProperty(0.0),
    val tagsProp: SimpleStringProperty = SimpleStringProperty(""),
    val encryptedProp: SimpleBooleanProperty = SimpleBooleanProperty(false),
    val cloudBackupProp: SimpleBooleanProperty = SimpleBooleanProperty(false),
    val uuidProp: SimpleStringProperty = SimpleStringProperty(""),
    val fsTypeProp: SimpleStringProperty = SimpleStringProperty(""),
    val hiddenProp: SimpleBooleanProperty = SimpleBooleanProperty(false),
    val virtualProp: SimpleBooleanProperty = SimpleBooleanProperty(false)
) {
    var id: Long get() = idProp.get(); set(v) = idProp.set(v)
    var diskId: Long get() = diskIdProp.get(); set(v) = diskIdProp.set(v)
    var name: String get() = nameProp.get(); set(v) = nameProp.set(v)
    var letter: String get() = letterProp.get(); set(v) = letterProp.set(v)
    var type: String get() = typeProp.get(); set(v) = typeProp.set(v)
    var sizeMB: Double get() = sizeMBProp.get(); set(v) = sizeMBProp.set(v)
    var usedMB: Double get() = usedMBProp.get(); set(v) = usedMBProp.set(v)

    // Wrapper for compatibility - internally stored in MB
    var sizeTB: Double get() = sizeMB / 1024.0; set(v) = run { sizeMB = v * 1024.0 }
    var usedTB: Double get() = usedMB / 1024.0; set(v) = run { usedMB = v * 1024.0 }
    var tags: String get() = tagsProp.get(); set(v) = tagsProp.set(v)
    var encrypted: Boolean get() = encryptedProp.get(); set(v) = encryptedProp.set(v)
    var cloudBackup: Boolean get() = cloudBackupProp.get(); set(v) = cloudBackupProp.set(v)
    var uuid: String get() = uuidProp.get(); set(v) = uuidProp.set(v)
    var fsType: String get() = fsTypeProp.get(); set(v) = fsTypeProp.set(v)
    var hidden: Boolean get() = hiddenProp.get(); set(v) = hiddenProp.set(v)
    var virtual: Boolean get() = virtualProp.get(); set(v) = virtualProp.set(v)

    override fun toString(): String {
        return "Partition($letter:$name ${sizeMB.formatSize()} ($uuid))"
    }
}

data class Disk(
    val idProp: SimpleLongProperty = SimpleLongProperty(0L),
    val nameProp: SimpleStringProperty = SimpleStringProperty(""),
    val sizeMBProp: SimpleDoubleProperty = SimpleDoubleProperty(0.0),
    val typeProp: SimpleStringProperty = SimpleStringProperty(DiskType.HD.name),
    val modelProp: SimpleStringProperty = SimpleStringProperty(""),
    val manufacturerProp: SimpleStringProperty = SimpleStringProperty(""),
    val serialProp: SimpleStringProperty = SimpleStringProperty(""),
    val tagProp: SimpleStringProperty = SimpleStringProperty(""),
    val hiddenProp: SimpleBooleanProperty = SimpleBooleanProperty(false),
    val partitions: MutableList<Partition> = mutableListOf()
) {
    var id: Long get() = idProp.get(); set(v) = idProp.set(v)
    var name: String get() = nameProp.get(); set(v) = nameProp.set(v)
    var sizeMB: Double get() = sizeMBProp.get(); set(v) = sizeMBProp.set(v)

    // Wrapper for compatibility - internally stored in MB
    var sizeTB: Double get() = sizeMB / 1024.0; set(v) = run { sizeMB = v * 1024.0 }
    var type: String get() = typeProp.get(); set(v) = typeProp.set(v)
    var model: String get() = modelProp.get(); set(v) = modelProp.set(v)
    var manufacturer: String get() = manufacturerProp.get(); set(v) = manufacturerProp.set(v)
    var serial: String get() = serialProp.get(); set(v) = serialProp.set(v)
    var tag: String get() = tagProp.get(); set(v) = tagProp.set(v)
    var hidden: Boolean get() = hiddenProp.get(); set(v) = hiddenProp.set(v)
    val usedMB: Double get() = partitions.sumOf { it.usedMB }
    val usedTB: Double get() = partitions.sumOf { it.usedTB }

    override fun toString(): String {
        return "Partition($manufacturer $name ${sizeMB.formatSize()} ($serial))"
    }
}

object SampleDataRepository {
    fun sampleDisks(): MutableList<Disk> = mutableListOf(
        Disk().apply {
            name = "1 TB SSD"
            sizeMB = 1.0 * 1024
            type = DiskType.SSD.name
            model = "SanDisk SDSSD..."
            tag = "Ultrabook"
            partitions += Partition().apply {
                name = "C: Win"; letter = "C"; type = PartitionType.Partition.name
                sizeMB = 0.9 * 1024; usedMB = 0.45 * 1024
            }
        },
        Disk().apply {
            name = "8 TB HD"; sizeMB = 8.0 * 1024; type = DiskType.HD.name; model = "Seagate ..."; tag = "NAS"
            partitions += Partition().apply {
                name = "D: Eigenes"; letter = "D"; sizeMB = 2.88 * 1024; usedMB = 1.5 * 1024
            }
            partitions += Partition().apply {
                name = "H: Data"; letter = "H"; sizeMB = 3.18 * 1024; usedMB = 2.3 * 1024
            }
            partitions += Partition().apply {
                name = "G: Install"; letter = "G"; sizeMB = 1.2 * 1024; usedMB = 0.6 * 1024
            }
        },
        Disk().apply {
            name = "10 TB HD"; sizeMB = 10.0 * 1024; type = DiskType.HD.name; model = "Seagate ..."; tag = "Media"
            partitions += Partition().apply {
                name = "M: Movie"; letter = "M"; sizeMB = 9.09 * 1024; usedMB = 6.2 * 1024; type =
                PartitionType.EncryptedContainer.name
            }
        },
        Disk().apply {
            name = "8 TB HD"; sizeMB = 8.0 * 1024; type = DiskType.HD.name; model = "Archive"; tag = "Archive"
            partitions += Partition().apply {
                name = "X: Stuff"; letter = "X"; sizeMB = 7.27 * 1024; usedMB = 4.26 * 1024
            }
            partitions += Partition().apply { name = "Movie 2"; sizeMB = 0.5 * 1024; usedMB = 0.48 * 1024 }
            partitions += Partition().apply { name = "XXX"; sizeMB = 3.5 * 1024; usedMB = 3.2 * 1024 }
            partitions += Partition().apply { name = "Steam"; sizeMB = 0.26 * 1024; usedMB = 0.2 * 1024 }
        },
        Disk().apply {
            name = "4 TB HD"; sizeMB = 4.0 * 1024; type = DiskType.HD.name; model = "WD ..."; tag = "Games"
            partitions += Partition().apply {
                name = "I: Stuff"; letter = "I"; sizeMB = 3.63 * 1024; usedMB = 1.6 * 1024
            }
            partitions += Partition().apply { name = "Outsourc."; sizeMB = 2.0 * 1024; usedMB = 1.5 * 1024 }
            partitions += Partition().apply { name = "Progs"; sizeMB = 0.8 * 1024; usedMB = 0.5 * 1024 }
            partitions += Partition().apply { name = "Games"; sizeMB = 0.5 * 1024; usedMB = 0.4 * 1024 }
        }
    )
}

fun Double.formatSize(): String {
    val displayUnit = Settings.displayUnit

    return when (displayUnit) {
        DisplayUnit.TB -> {
            val valueTB =
                this / 1024.0 / 1024.0  // MB to TB: divide by 1024 twice (1 MB = 0.00098 TB via 1024 MB = 1 GB, 1024 GB = 1 TB)
            if (valueTB >= 1) String.format("%.0f TB", valueTB) else String.format("%.1f TB", valueTB)
        }

        DisplayUnit.GB -> {
            val valueGB = this / 1024.0  // MB to GB: divide by 1024
            if (valueGB >= 1) String.format("%.0f GB", valueGB) else String.format("%.1f GB", valueGB)
        }

        DisplayUnit.MB -> {
            if (this >= 1024) String.format("%.0f MB", this) else String.format("%.1f MB", this)
        }
    }
}

fun Double.percentOf(total: Double): Double = if (total <= 0.0) 0.0 else (this / total).coerceIn(0.0, 1.0)

