package de.tfr.tool.model

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleLongProperty
import javafx.beans.property.SimpleStringProperty

enum class DiskType { SSD, HD }
enum class PartitionType { Partition, EncryptedContainer }

data class Partition(
    val idProp: SimpleLongProperty = SimpleLongProperty(0L),
    val diskIdProp: SimpleLongProperty = SimpleLongProperty(0L),
    val nameProp: SimpleStringProperty = SimpleStringProperty(""),
    val letterProp: SimpleStringProperty = SimpleStringProperty(""),
    val typeProp: SimpleStringProperty = SimpleStringProperty(PartitionType.Partition.name),
    val sizeTBProp: SimpleDoubleProperty = SimpleDoubleProperty(0.0),
    val usedTBProp: SimpleDoubleProperty = SimpleDoubleProperty(0.0),
    val tagsProp: SimpleStringProperty = SimpleStringProperty(""),
    val encryptedProp: SimpleBooleanProperty = SimpleBooleanProperty(false),
    val cloudBackupProp: SimpleBooleanProperty = SimpleBooleanProperty(false),
    val uuidProp: SimpleStringProperty = SimpleStringProperty(""),
    val fsTypeProp: SimpleStringProperty = SimpleStringProperty(""),
    val hiddenProp: SimpleBooleanProperty = SimpleBooleanProperty(false),
    val virtualProp: SimpleBooleanProperty = SimpleBooleanProperty(false) // NEW: indicates that the partition is virtual (e.g., encrypted container, loopback, network volume)
) {
    var id: Long get() = idProp.get(); set(v) = idProp.set(v)
    var diskId: Long get() = diskIdProp.get(); set(v) = diskIdProp.set(v)
    var name: String get() = nameProp.get(); set(v) = nameProp.set(v)
    var letter: String get() = letterProp.get(); set(v) = letterProp.set(v)
    var type: String get() = typeProp.get(); set(v) = typeProp.set(v)
    var sizeTB: Double get() = sizeTBProp.get(); set(v) = sizeTBProp.set(v)
    var usedTB: Double get() = usedTBProp.get(); set(v) = usedTBProp.set(v)
    var tags: String get() = tagsProp.get(); set(v) = tagsProp.set(v)
    var encrypted: Boolean get() = encryptedProp.get(); set(v) = encryptedProp.set(v)
    var cloudBackup: Boolean get() = cloudBackupProp.get(); set(v) = cloudBackupProp.set(v)
    var uuid: String get() = uuidProp.get(); set(v) = uuidProp.set(v)
    var fsType: String get() = fsTypeProp.get(); set(v) = fsTypeProp.set(v)
    var hidden: Boolean get() = hiddenProp.get(); set(v) = hiddenProp.set(v)
    var virtual: Boolean get() = virtualProp.get(); set(v) = virtualProp.set(v)

    override fun toString(): String {
        return "Partition($letter:$name ${sizeTB.toTBString()} ($uuid))"
    }
}

data class Disk(
    val idProp: SimpleLongProperty = SimpleLongProperty(0L),
    val nameProp: SimpleStringProperty = SimpleStringProperty(""),
    val sizeTBProp: SimpleDoubleProperty = SimpleDoubleProperty(0.0),
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
    var sizeTB: Double get() = sizeTBProp.get(); set(v) = sizeTBProp.set(v)
    var type: String get() = typeProp.get(); set(v) = typeProp.set(v)
    var model: String get() = modelProp.get(); set(v) = modelProp.set(v)
    var manufacturer: String get() = manufacturerProp.get(); set(v) = manufacturerProp.set(v)
    var serial: String get() = serialProp.get(); set(v) = serialProp.set(v)
    var tag: String get() = tagProp.get(); set(v) = tagProp.set(v)
    var hidden: Boolean get() = hiddenProp.get(); set(v) = hiddenProp.set(v)
    val usedTB: Double get() = partitions.sumOf { it.usedTB }

    override fun toString(): String {
        return "Partition($manufacturer $name ${sizeTB.toTBString()} ($serial))"
    }
}

object SampleDataRepository {
    fun sampleDisks(): MutableList<Disk> = mutableListOf(
        Disk().apply {
            name = "1 TB SSD"
            sizeTB = 1.0
            type = DiskType.SSD.name
            model = "SanDisk SDSSD..."
            tag = "Ultrabook"
            partitions += Partition().apply {
                name = "C: Win"; letter = "C"; type = PartitionType.Partition.name
                sizeTB = 0.9; usedTB = 0.45
            }
        },
        Disk().apply {
            name = "8 TB HD"; sizeTB = 8.0; type = DiskType.HD.name; model = "Seagate ..."; tag = "NAS"
            partitions += Partition().apply { name = "D: Eigenes"; letter = "D"; sizeTB = 2.88; usedTB = 1.5 }
            partitions += Partition().apply { name = "H: Data"; letter = "H"; sizeTB = 3.18; usedTB = 2.3 }
            partitions += Partition().apply { name = "G: Install"; letter = "G"; sizeTB = 1.2; usedTB = 0.6 }
        },
        Disk().apply {
            name = "10 TB HD"; sizeTB = 10.0; type = DiskType.HD.name; model = "Seagate ..."; tag = "Media"
            partitions += Partition().apply { name = "M: Movie"; letter = "M"; sizeTB = 9.09; usedTB = 6.2; type = PartitionType.EncryptedContainer.name }
        },
        Disk().apply {
            name = "8 TB HD"; sizeTB = 8.0; type = DiskType.HD.name; model = "Archive"; tag = "Archive"
            partitions += Partition().apply { name = "X: Stuff"; letter = "X"; sizeTB = 7.27; usedTB = 4.26 }
            partitions += Partition().apply { name = "Movie 2"; sizeTB = 0.5; usedTB = 0.48 }
            partitions += Partition().apply { name = "XXX"; sizeTB = 3.5; usedTB = 3.2 }
            partitions += Partition().apply { name = "Steam"; sizeTB = 0.26; usedTB = 0.2 }
        },
        Disk().apply {
            name = "4 TB HD"; sizeTB = 4.0; type = DiskType.HD.name; model = "WD ..."; tag = "Games"
            partitions += Partition().apply { name = "I: Stuff"; letter = "I"; sizeTB = 3.63; usedTB = 1.6 }
            partitions += Partition().apply { name = "Outsourc."; sizeTB = 2.0; usedTB = 1.5 }
            partitions += Partition().apply { name = "Progs"; sizeTB = 0.8; usedTB = 0.5 }
            partitions += Partition().apply { name = "Games"; sizeTB = 0.5; usedTB = 0.4 }
        }
    )
}

fun Double.toTBString(): String = if (this >= 1) String.format("%.0f TB", this) else String.format("%.1f TB", this)
fun Double.percentOf(total: Double): Double = if (total <= 0.0) 0.0 else (this / total).coerceIn(0.0, 1.0)
