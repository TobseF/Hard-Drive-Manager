package de.tfr.tool.persist

import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import oshi.SystemInfo
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Reads hardware information via OSHI and performs a non-destructive
 * enrichment/merge into the existing DB.
 *
 * Matching rules:
 *  - Disks: primarily by serial number; fallback: model + size.
 *  - Partitions: primarily by UUID; fallback: drive letter/mount point.
 *
 * Empty fields are filled; sizes/used values are updated.
 */
object OshiImporter {
    data class Result(
        val disksUpdated: Int,
        val disksInserted: Int,
        val partitionsUpdated: Int,
        val partitionsInserted: Int
    )

    fun readAndMerge(): Result {
        val systemInfo = SystemInfo()
        val hardwareAbstractionLayer = systemInfo.hardware
        val os = systemInfo.operatingSystem

        val existing = DiskRepository.loadAll()
        val diskBySerial = existing.associateBy { it.serial.trim().lowercase() }.toMutableMap()
        val disksByModelSize = existing.associateBy { (it.model.trim().lowercase()) + "|" + it.sizeTB.toString() }.toMutableMap()

        // Map Partitionen nach UUID und Laufwerksbuchstabe
        val partByUuid = existing.flatMap { it.partitions }.associateBy { it.uuid.trim().lowercase() }.toMutableMap()
        val partByLetter = existing.flatMap { it.partitions }.associateBy { it.letter.trim().lowercase() }.toMutableMap()

        val disksUpdated = AtomicInteger(0)
        val disksInserted = AtomicInteger(0)
        val partitionsUpdated = AtomicInteger(0)
        val partitionsInserted = AtomicInteger(0)

        // Dateien-/Volume-Infos (für Partitionen)
        val fileSystem = os.fileSystem
        val stores = fileSystem.fileStores
        // Map nach UUID und Mount
        val storeByUuid = stores.associateBy { (it.uuid ?: "").trim().lowercase() }
        val storeByMount = stores.associateBy { (it.mount ?: it.name ?: "").trim().lowercase() }


        // Disks auslesen
        val oshiDisks = hardwareAbstractionLayer.diskStores
        oshiDisks.forEach { diskStore ->
            val serial = (diskStore.serial ?: "").trim()
            val modelSystemInfo = (diskStore.model ?: "").trim()
            val manufacturer = guessManufacturer(modelSystemInfo)
            val model = parseModel(modelSystemInfo, manufacturer)
            val sizeTB = bytesToTB(diskStore.size)
            val type = guessType(model)
            val keySerial = serial.lowercase()
            var disk: Disk? = if (keySerial.isNotEmpty()) diskBySerial[keySerial] else null
            if (disk == null) {
                disk = disksByModelSize[model.lowercase() + "|" + sizeTB.toString()]
            }

            val generatedDriveName = "$manufacturer $type"
            if (disk == null) {
                // create new
                val newDisk = Disk().apply {
                    name = generatedDriveName
                    this.model = model
                    this.manufacturer = manufacturer
                    this.serial = serial
                    this.type = type
                    this.sizeTB = sizeTB
                    this.tag = ""
                }
                val newId = DiskRepository.insertDisk(newDisk)
                newDisk.id = newId
                existing += newDisk
                if (serial.isNotEmpty()) diskBySerial[serial.lowercase()] = newDisk
                disksByModelSize[model.lowercase() + "|" + sizeTB.toString()] = newDisk
                disk = newDisk
                disksInserted.incrementAndGet()
            } else {
                // complement/update
                var changed = false
                if (disk.sizeTB != sizeTB && sizeTB > 0) { disk.sizeTB = sizeTB; changed = true }
                if (disk.model.isBlank() && model.isNotEmpty()) { disk.model = model; changed = true }
                if (disk.manufacturer.isBlank() && manufacturer.isNotEmpty()) { disk.manufacturer = manufacturer; changed = true }
                if (disk.serial.isBlank() && serial.isNotEmpty()) { disk.serial = serial; changed = true }
                if (disk.type.isBlank() && type.isNotEmpty()) { disk.type = type; changed = true }
                if (disk.name.isBlank()) { disk.name = generatedDriveName; changed = true }
                if (changed) { DiskRepository.updateDisk(disk); disksUpdated.incrementAndGet() }
            }

            // Map partitions
            diskStore.partitions.forEach { part ->
                val uuid = (part.uuid ?: "").trim()
                val mount = (part.mountPoint ?: part.identification ?: "").trim()
                val pSizeTB = bytesToTB(part.size)
                // used TB via FileStore (if mount/uuid matches)
                var usedTB = 0.0
                val store = when {
                    uuid.isNotEmpty() && storeByUuid.containsKey(uuid.lowercase()) -> storeByUuid[uuid.lowercase()]
                    mount.isNotEmpty() && storeByMount.containsKey(mount.lowercase()) -> storeByMount[mount.lowercase()]
                    else -> null
                }
                var label = ""
                var mountPoint = ""
                if (store != null) {
                    usedTB = bytesToTB(store.totalSpace - store.usableSpace)
                    label = store.label
                    mountPoint = store.mount
                }
                val driveLetter = extractWindowsLetter(mountPoint)


                val keyUuid = uuid.lowercase()
                var partitionFromDB: Partition? = if (keyUuid.isNotEmpty()) partByUuid[keyUuid] else null
                if (partitionFromDB == null && driveLetter.isNotEmpty()) {
                    if (driveLetter.isNotEmpty()) partitionFromDB = partByLetter[driveLetter.lowercase()]
                }


                if (partitionFromDB == null) {
                    val outerUsed = usedTB
                    val outerUuid = uuid
                    val fsTypeStr = store?.type ?: ""
                    val newPartition = Partition().apply {
                        this.diskId = disk.id
                        this.name = label
                        this.letter = driveLetter
                        this.type = "Partition"
                        this.sizeTB = pSizeTB
                        this.usedTB = outerUsed
                        this.uuid = outerUuid
                        this.fsType = fsTypeStr
                        this.tags = ""
                    }
                    val pid = DiskRepository.insertPartition(newPartition)
                    newPartition.id = pid
                    disk.partitions += newPartition
                    if (outerUuid.isNotEmpty()) partByUuid[outerUuid.lowercase()] = newPartition
                    if (newPartition.letter.isNotBlank()) partByLetter[newPartition.letter.lowercase()] = newPartition
                    partitionsInserted.incrementAndGet()
                } else {
                    var changed = false
                    if (partitionFromDB.diskId != disk.id) { partitionFromDB.diskId = disk.id; changed = true }
                    if (partitionFromDB.name != label) { partitionFromDB.name = label; changed = true }
                    if (partitionFromDB.sizeTB != pSizeTB && pSizeTB > 0) { partitionFromDB.sizeTB = pSizeTB; changed = true }
                    if (usedTB >= 0.0 && abs(partitionFromDB.usedTB - usedTB) > 0.0001) { partitionFromDB.usedTB = usedTB; changed = true }
                    if (partitionFromDB.uuid.isBlank() && uuid.isNotEmpty()) { partitionFromDB.uuid = uuid; changed = true }
                    val letter = extractWindowsLetter(mount)
                    if (partitionFromDB.letter.isBlank() && letter.isNotEmpty()) { partitionFromDB.letter = letter; changed = true }
                    val fsType = store?.type ?: ""
                    if (partitionFromDB.fsType.isBlank() && fsType.isNotEmpty()) { partitionFromDB.fsType = fsType; changed = true }
                    if (changed) { DiskRepository.updatePartition(partitionFromDB); partitionsUpdated.incrementAndGet() }
                }
            }
        }

        return Result(disksUpdated.get(), disksInserted.get(), partitionsUpdated.get(), partitionsInserted.get())
    }

    /**
     * Reads the model name out of the localized system model info.
     *
     * For example, input: `TOSHIBA MG09ACA18TE (Standardlaufwerke)`
     *
     * Output: `MG09ACA18TE`
     */
    private fun parseModel(model: String, manufacturer: String): String{
        val modelWithoutInfo = model.substringBefore("(").trim()
        if(manufacturer.isEmpty()){
            return modelWithoutInfo;
        }else{
            return modelWithoutInfo.replace(manufacturer, "", ignoreCase = true).trim()
        }
    }


    private fun bytesToTB(bytes: Long): Double {
        if (bytes <= 0) return 0.0
        return bytes.toDouble() / (1024.0 * 1024.0 * 1024.0 * 1024.0)
    }

    private fun guessManufacturer(model: String): String {
        val m = model.lowercase()
        return when {
            m.contains("samsung") -> "Samsung"
            m.contains("sandisk") -> "SanDisk"
            m.contains("seagate") || m.startsWith("st") -> "Seagate"
            m.contains("lexar") -> "Lexar"
            m.startsWith("ct") -> "Crucial"
            m.contains("western digital") || m.contains("wd ") || m.contains("wdc") -> "Western Digital"
            m.contains("toshiba") -> "Toshiba"
            else -> ""
        }
    }

    private fun guessType(model: String): String {
        val m = model.lowercase()
        return when {
            m.contains("ssd") || m.contains("nvme") -> "SSD"
            else -> "HD"
        }
    }

    private fun extractWindowsLetter(mount: String): String {
        // Example: "C:\\" → "C"
        val trimmed = mount.trim()
        return trimmed.substringBefore(":").uppercase()
    }

    private fun buildPartitionName(mount: String): String {
        val letter = extractWindowsLetter(mount)
        return if (letter.isNotEmpty()) "$letter:" else mount.ifBlank { "Partition" }
    }
}
