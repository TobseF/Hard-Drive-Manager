package de.tfr.tool.model

import de.tfr.tool.persist.Settings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleStringProperty

enum class DiskType(val label: String) { SSD("SSD"), HD("HD"), M2("M.2") }
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
    val virtualProp: SimpleBooleanProperty = SimpleBooleanProperty(false),
    val commentProp: SimpleStringProperty = SimpleStringProperty("")
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
    var comment: String get() = commentProp.get(); set(v) = commentProp.set(v)

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
    val commentProp: SimpleStringProperty = SimpleStringProperty(""),
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
    var comment: String get() = commentProp.get(); set(v) = commentProp.set(v)
    val usedMB: Double get() = partitions.sumOf { it.usedMB }
    val usedTB: Double get() = partitions.sumOf { it.usedTB }

    override fun toString(): String {
        return "Partition($manufacturer $name ${sizeMB.formatSize()} ($serial))"
    }
}

object SampleDataRepository {

    private fun parseSizeString(size: String): Double {
        val parts = size.trim().split(" ")
        val value = parts[0].toDoubleOrNull() ?: 0.0
        val unit = parts.getOrNull(1)?.uppercase() ?: "MB"

        return when (unit) {
            "TB" -> value * 1024.0 * 1024.0
            "GB" -> value * 1024.0
            "MB" -> value
            else -> value
        }
    }

    private fun parseDiskType(type: String): String {
        return when {
            type.contains("M.2", ignoreCase = true) -> DiskType.M2.label
            type.contains("SSD", ignoreCase = true) -> DiskType.SSD.label
            type.contains("HDD", ignoreCase = true) -> DiskType.HD.label
            else -> DiskType.HD.label
        }
    }

    fun sampleDisks(): MutableList<Disk> = mutableListOf(
        // Disk 1: Samsung 980 PRO System
        Disk().apply {
            id = 1L
            name = "980 PRO System"
            manufacturer = "Samsung"
            sizeMB = parseSizeString("1 TB")
            type = parseDiskType("SSD (M.2 NVMe)")
            model = "MZ-V8P1T0BW"
            serial = "S5GXNX0T821493K"
            partitions += Partition().apply {
                id = 1L; diskId = 1L
                name = "EFI System"; letter = ""
                sizeMB = parseSizeString("100 MB"); usedMB = parseSizeString("45 MB")
                fsType = "FAT32"; type = PartitionType.Partition.name
                hidden = true; virtual = false; encrypted = false; cloudBackup = false
                tags = "System, Hidden"
                comment = "EFI + Bootloader"
            }
            partitions += Partition().apply {
                id = 2L; diskId = 1L
                name = "Windows"; letter = "C"
                sizeMB = parseSizeString("900 GB"); usedMB = parseSizeString("420 GB")
                fsType = "NTFS"; type = PartitionType.Partition.name
                hidden = false; virtual = false; encrypted = true; cloudBackup = false
                tags = "OS, Programme, Critical"
                comment = "Primary system volume"
            }
            partitions += Partition().apply {
                id = 3L; diskId = 1L
                name = "WinRE"; letter = ""
                sizeMB = parseSizeString("900 MB"); usedMB = parseSizeString("650 MB")
                fsType = "NTFS"; type = PartitionType.Partition.name
                hidden = false; virtual = false; encrypted = false; cloudBackup = false
                tags = "Recovery, System"
            }
        },
        // Disk 2: Western Digital WD_BLACK SN850X
        Disk().apply {
            id = 2L
            name = "WD_BLACK SN850X"
            manufacturer = "Western Digital"
            sizeMB = parseSizeString("2 TB")
            type = parseDiskType("SSD (M.2 NVMe)")
            model = "WDS200T2X0E"
            serial = "22453G804511"
            partitions += Partition().apply {
                id = 4L; diskId = 2L
                name = "Games Library"; letter = "G"
                sizeMB = parseSizeString("2 TB"); usedMB = parseSizeString("1.4 TB")
                fsType = "NTFS"; type = PartitionType.Partition.name
                hidden = false; virtual = false; encrypted = false; cloudBackup = false
                tags = "Steam, Epic Games, High Performance"
            }
        },
        // Disk 3: Seagate IronWolf Pro
        Disk().apply {
            id = 3L
            name = "IronWolf Pro"
            manufacturer = "Seagate"
            sizeMB = parseSizeString("8 TB")
            type = parseDiskType("HDD (SATA)")
            model = "ST8000NE001"
            serial = "Z305X6A9"
            partitions += Partition().apply {
                id = 5L; diskId = 3L
                name = "Movies & TV"; letter = "M"
                sizeMB = parseSizeString("6 TB"); usedMB = parseSizeString("4.2 TB")
                fsType = "exFAT"; type = PartitionType.Partition.name
                hidden = false; virtual = false; encrypted = false; cloudBackup = false
                tags = "Plex, Medien, 4K Video"
            }
            partitions += Partition().apply {
                id = 6L; diskId = 3L
                name = "Music Archive"; letter = "A"
                sizeMB = parseSizeString("2 TB"); usedMB = parseSizeString("800 GB")
                fsType = "NTFS"; type = PartitionType.Partition.name
                hidden = false; virtual = false; encrypted = false; cloudBackup = true
                tags = "FLAC, MP3, Archiv"
            }
        },
        // Disk 4: Kingston KC3000 Dev
        Disk().apply {
            id = 4L
            name = "KC3000 Dev"
            manufacturer = "Kingston"
            sizeMB = parseSizeString("2 TB")
            type = parseDiskType("SSD (M.2 NVMe)")
            model = "SKC3000D/2048G"
            serial = "50026B7685D0C1A2"
            partitions += Partition().apply {
                id = 7L; diskId = 4L
                name = "Repositories"; letter = "D"
                sizeMB = parseSizeString("500 GB"); usedMB = parseSizeString("120 GB")
                fsType = "NTFS"; type = PartitionType.Partition.name
                hidden = false; virtual = false; encrypted = true; cloudBackup = false
                tags = "Git, Work, Source Code"
            }
            partitions += Partition().apply {
                id = 8L; diskId = 4L
                name = "AI Training Data"; letter = "X"
                sizeMB = parseSizeString("1 TB"); usedMB = parseSizeString("850 GB")
                fsType = "NTFS"; type = PartitionType.Partition.name
                hidden = false; virtual = false; encrypted = false; cloudBackup = false
                tags = "PyTorch, Datasets, LLM, No Backup"
                comment = "Keep 15% free for staging"
            }
            partitions += Partition().apply {
                id = 9L; diskId = 4L
                name = "WSL_VirtualDisk"; letter = ""
                sizeMB = parseSizeString("500 GB"); usedMB = parseSizeString("200 GB")
                fsType = "EXT4"; type = PartitionType.Partition.name
                hidden = false; virtual = true; encrypted = false; cloudBackup = false
                tags = "Linux, Ubuntu, Docker"
            }
        },
        // Disk 5: Western Digital WD Blue Archive
        Disk().apply {
            id = 5L
            name = "WD Blue Archive"
            manufacturer = "Western Digital"
            sizeMB = parseSizeString("4 TB")
            type = parseDiskType("HDD (SATA)")
            model = "WD40EZAZ"
            serial = "WCC4M1SX9L2P"
            partitions += Partition().apply {
                id = 10L; diskId = 5L
                name = "Cold Storage"; letter = "B"
                sizeMB = parseSizeString("4 TB"); usedMB = parseSizeString("3.1 TB")
                fsType = "NTFS"; type = PartitionType.Partition.name
                hidden = false; virtual = false; encrypted = true; cloudBackup = true
                tags = "Backup, Veeam, Encrypted, Offsite Copy"
            }
        },
        // Disk 6: Crucial MX500 Scratch
        Disk().apply {
            id = 6L
            name = "MX500 Scratch"
            manufacturer = "Crucial"
            sizeMB = parseSizeString("500 GB")
            type = parseDiskType("SSD (SATA)")
            model = "CT500MX500SSD1"
            serial = "2138E5482910"
            partitions += Partition().apply {
                id = 11L; diskId = 6L
                name = "Scratch Disk"; letter = "T"
                sizeMB = parseSizeString("250 GB"); usedMB = parseSizeString("10 GB")
                fsType = "exFAT"; type = PartitionType.Partition.name
                hidden = false; virtual = false; encrypted = false; cloudBackup = false
                tags = "Temp, Adobe Cache, Trash"
            }
            partitions += Partition().apply {
                id = 12L; diskId = 6L
                name = "VM Playground"; letter = "V"
                sizeMB = parseSizeString("250 GB"); usedMB = parseSizeString("180 GB")
                fsType = "NTFS"; type = PartitionType.Partition.name
                hidden = false; virtual = true; encrypted = false; cloudBackup = false
                tags = "VirtualBox, Sandbox, Testing"
            }
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
