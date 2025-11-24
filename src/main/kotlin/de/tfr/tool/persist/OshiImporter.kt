package de.tfr.tool.persist

import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import de.tfr.tool.persist.OshiImporter.bytesToMB
import oshi.SystemInfo
import oshi.hardware.HWDiskStore
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
 *
 * Implementation detail: We first discover all partitions (including file stores that may not
 * be reported as disk partitions – e.g. encrypted containers or virtual volumes) and afterwards
 * associate/create disks for them. This prevents partitions from being lost when their underlying
 * disk is not exposed by OSHI or cannot be matched.
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

        // Load existing disks and build lookup maps
        val existingDisks = DiskRepository.loadAll()
        val diskBySerial = existingDisks.associateBy { it.serial.trim().lowercase() }.toMutableMap()
        val disksByModelSize =
            existingDisks.associateBy { (it.model.trim().lowercase()) + "|" + it.sizeTB.toString() }.toMutableMap()

        // Existing partitions lookup maps (UUID + drive letter)
        val partitionByUuid: MutableMap<String, Partition> =
            existingDisks.flatMap { it.partitions }.associateBy { it.uuid.trim().lowercase() }.toMutableMap()
        val partitionByLetter: MutableMap<String, Partition> =
            existingDisks.flatMap { it.partitions }.associateBy { it.letter.trim().lowercase() }.toMutableMap()

        val disksUpdated = AtomicInteger(0)
        val disksInserted = AtomicInteger(0)
        val partitionsUpdated = AtomicInteger(0)
        val partitionsInserted = AtomicInteger(0)

        // File system stores (volumes) - may include encrypted containers or virtual volumes
        val fileStores = os.fileSystem.fileStores

        // Disk stores from hardware layer
        val hwDiskStores = hardwareAbstractionLayer.diskStores



        val partitionCandidates = mutableListOf<PartitionCandidate>()

        // 1. Collect partitions from disk stores (hardware reported partitions)
        hwDiskStores.forEach { diskStore ->
            diskStore.partitions.forEach { part ->
                val uuid = (part.uuid ?: "").trim()
                val mount = (part.mountPoint ?: part.identification ?: "").trim()
                val sizeBytes = part.size
                partitionCandidates += PartitionCandidate(uuid, mount, sizeBytes, diskStore)
            }
        }

        // Helper maps for quick candidate enrichment
        fun findCandidate(uuid: String, mount: String): PartitionCandidate? {
            val u = uuid.trim().lowercase()
            val m = mount.trim().lowercase()
            return partitionCandidates.firstOrNull {
                (it.uuid.trim().lowercase().takeIf { it.isNotEmpty() } == u && u.isNotEmpty()) ||
                        (it.mountRaw.trim().lowercase().takeIf { it.isNotEmpty() } == m && m.isNotEmpty())
            }
        }

        // 2. Enrich with file store information or add missing volumes as standalone partitions
        fileStores.forEach { store ->
            val sUuid = (store.uuid ?: "").trim()
            val sMount = (store.mount ?: store.name ?: "").trim()
            val candidate = findCandidate(sUuid, sMount)
            val usedMB = bytesToMB(store.totalSpace - store.usableSpace)
            if (candidate != null) {
                candidate.usedMB = usedMB
                candidate.label = store.label ?: ""
                candidate.mountPoint = sMount
                candidate.fsType = store.type ?: ""
            } else {
                // Missing in disk partitions -> add new candidate without associated disk (will go to Unknown Disk)
                partitionCandidates += PartitionCandidate(
                    uuid = sUuid,
                    mountRaw = sMount,
                    sizeBytes = store.totalSpace, // approximate size
                    diskStore = null,
                    usedMB = usedMB,
                    label = store.label ?: "",
                    mountPoint = sMount,
                    fsType = store.type ?: ""
                )
            }
        }

        // Unknown disk placeholder (lazy create) for partitions without backing disk info
        var unknownDisk: Disk? = null
        fun ensureUnknownDisk(): Disk {
            if (unknownDisk != null) return unknownDisk!!
            // Try to reuse existing disk with name "Unknown Disk" if present
            unknownDisk = existingDisks.firstOrNull { it.name == "Unknown Disk" }
            if (unknownDisk == null) {
                val d = Disk().apply {
                    name = "Unknown Disk"
                    type = ""
                    model = ""
                    manufacturer = ""
                    serial = ""
                    sizeTB = 0.0
                    tag = ""
                }
                val newId = DiskRepository.insertDisk(d)
                d.id = newId
                existingDisks += d
                disksByModelSize[d.model.lowercase() + "|" + d.sizeTB.toString()] = d
                unknownDisk = d
                disksInserted.incrementAndGet()
            }
            return unknownDisk!!
        }

        // Track which disk stores have been processed
        val processedDiskStores = mutableSetOf<HWDiskStore>()

        // 3. For each partition candidate resolve/create its disk, then merge partition
        partitionCandidates.forEach { partitionCandidate ->
            // Resolve disk
            val disk: Disk = if (partitionCandidate.diskStore != null) {
                val diskStore = partitionCandidate.diskStore
                processedDiskStores += diskStore
                val serial = (diskStore.serial ?: "").trim()
                val modelSystemInfo = (diskStore.model ?: "").trim()
                val manufacturer = guessManufacturer(modelSystemInfo)
                val model = parseModel(modelSystemInfo, manufacturer)
                val sizeMB = bytesToMB(diskStore.size)
                val type = guessType(model)
                val keySerial = serial.lowercase()
                var diskFromDB: Disk? = if (keySerial.isNotEmpty()) diskBySerial[keySerial] else null
                if (diskFromDB == null) {
                    diskFromDB = disksByModelSize[model.lowercase() + "|" + sizeMB.toString()]
                }
                val generatedDriveName = listOf(manufacturer, type).filter { it.isNotBlank() }.joinToString(" ")
                    .ifBlank { model.ifBlank { serial }.ifBlank { "Disk" } }
                if (diskFromDB == null) {
                    val newDisk = Disk().apply {
                        name = generatedDriveName
                        this.model = model
                        this.manufacturer = manufacturer
                        this.serial = serial
                        this.type = type
                        this.sizeMB = sizeMB
                        this.tag = ""
                    }
                    val newId = DiskRepository.insertDisk(newDisk)
                    newDisk.id = newId
                    existingDisks += newDisk
                    if (serial.isNotEmpty()) diskBySerial[serial.lowercase()] = newDisk
                    disksByModelSize[model.lowercase() + "|" + sizeMB.toString()] = newDisk
                    disksInserted.incrementAndGet()
                    newDisk
                } else {
                    var changed = false
                    if (diskFromDB.sizeMB != sizeMB && sizeMB > 0) {
                        diskFromDB.sizeMB = sizeMB; changed = true
                    }
                    if (diskFromDB.model.isBlank() && model.isNotEmpty()) {
                        diskFromDB.model = model; changed = true
                    }
                    if (diskFromDB.manufacturer.isBlank() && manufacturer.isNotEmpty()) {
                        diskFromDB.manufacturer = manufacturer; changed = true
                    }
                    if (diskFromDB.serial.isBlank() && serial.isNotEmpty()) {
                        diskFromDB.serial = serial; changed = true
                    }
                    if (diskFromDB.type.isBlank() && type.isNotEmpty()) {
                        diskFromDB.type = type; changed = true
                    }
                    if (diskFromDB.name.isBlank()) {
                        diskFromDB.name = generatedDriveName; changed = true
                    }
                    if (changed) {
                        DiskRepository.updateDisk(diskFromDB); disksUpdated.incrementAndGet()
                    }
                    diskFromDB
                }
            } else {
                // No disk info -> use Unknown Disk placeholder
                ensureUnknownDisk()
            }

            // Partition merge/create
            val uuidKey = partitionCandidate.uuid.trim().lowercase()
            val mountPoint = partitionCandidate.mountPoint.ifBlank { partitionCandidate.mountRaw }
            val letter = extractWindowsLetter(mountPoint)
            var partitionFromDB: Partition? = if (uuidKey.isNotEmpty()) partitionByUuid[uuidKey] else null
            if (partitionFromDB == null && letter.isNotEmpty()) {
                partitionFromDB = partitionByLetter[letter.lowercase()]
            }


            if (partitionFromDB == null) {
                val sizeMB = bytesToMB(partitionCandidate.sizeBytes)
                val usedMB = partitionCandidate.usedMB
                val fsType = partitionCandidate.fsType
                val label = partitionCandidate.label
                val newPartition = Partition().apply {
                    this.diskId = disk.id
                    this.name = label.ifBlank { if (letter.isNotEmpty()) "$letter" else "Unknown" }
                    this.letter = letter
                    this.type = "Partition"
                    this.sizeMB = sizeMB
                    this.usedMB = usedMB
                    this.uuid = partitionCandidate.uuid
                    this.fsType = fsType
                    this.tags = ""
                    this.virtual = partitionCandidate.diskStore == null
                }
                val pid = DiskRepository.insertPartition(newPartition)
                newPartition.id = pid
                disk.partitions += newPartition
                if (partitionCandidate.uuid.isNotBlank()) partitionByUuid[partitionCandidate.uuid.lowercase()] =
                    newPartition
                if (newPartition.letter.isNotBlank()) partitionByLetter[newPartition.letter.lowercase()] = newPartition
                partitionsInserted.incrementAndGet()
            } else {
                partitionFromDB.applyPartition(
                    disk,
                    partitionCandidate,
                    letter,
                    partitionsUpdated
                )
            }
        }

        // 4. Process disks without partitions that haven't been processed yet
        hwDiskStores.filterNot { it in processedDiskStores }.forEach { diskStore ->
            val serial = (diskStore.serial ?: "").trim()
            val modelSystemInfo = (diskStore.model ?: "").trim()
            val manufacturer = guessManufacturer(modelSystemInfo)
            val model = parseModel(modelSystemInfo, manufacturer)
            val sizeMB = bytesToMB(diskStore.size)
            val type = guessType(model)
            val keySerial = serial.lowercase()
            var diskFromDB: Disk? = if (keySerial.isNotEmpty()) diskBySerial[keySerial] else null
            if (diskFromDB == null) {
                diskFromDB = disksByModelSize[model.lowercase() + "|" + sizeMB.toString()]
            }
            val generatedDriveName = listOf(manufacturer, type).filter { it.isNotBlank() }.joinToString(" ")
                .ifBlank { model.ifBlank { serial }.ifBlank { "Disk" } }
            if (diskFromDB == null) {
                val newDisk = Disk().apply {
                    name = generatedDriveName
                    this.model = model
                    this.manufacturer = manufacturer
                    this.serial = serial
                    this.type = type
                    this.sizeMB = sizeMB
                    this.tag = ""
                }
                val newId = DiskRepository.insertDisk(newDisk)
                newDisk.id = newId
                existingDisks += newDisk
                if (serial.isNotEmpty()) diskBySerial[serial.lowercase()] = newDisk
                disksByModelSize[model.lowercase() + "|" + sizeMB.toString()] = newDisk
                disksInserted.incrementAndGet()
            } else {
                var changed = false
                if (diskFromDB.sizeMB != sizeMB && sizeMB > 0) {
                    diskFromDB.sizeMB = sizeMB; changed = true
                }
                if (diskFromDB.model.isBlank() && model.isNotEmpty()) {
                    diskFromDB.model = model; changed = true
                }
                if (diskFromDB.manufacturer.isBlank() && manufacturer.isNotEmpty()) {
                    diskFromDB.manufacturer = manufacturer; changed = true
                }
                if (diskFromDB.serial.isBlank() && serial.isNotEmpty()) {
                    diskFromDB.serial = serial; changed = true
                }
                if (diskFromDB.type.isBlank() && type.isNotEmpty()) {
                    diskFromDB.type = type; changed = true
                }
                if (diskFromDB.name.isBlank()) {
                    diskFromDB.name = generatedDriveName; changed = true
                }
                if (changed) {
                    DiskRepository.updateDisk(diskFromDB); disksUpdated.incrementAndGet()
                }
            }
        }

        return Result(disksUpdated.get(), disksInserted.get(), partitionsUpdated.get(), partitionsInserted.get())
    }

    // Candidate structure for partition-first processing
    data class PartitionCandidate(
        val uuid: String,
        val mountRaw: String,
        val sizeBytes: Long,
        val diskStore: HWDiskStore?,
        var usedMB: Double = 0.0,
        var label: String = "",
        var mountPoint: String = "",
        var fsType: String = ""
    )

    /**
     * Reads the model name out of the localized system model info.
     *
     * For example, input: `TOSHIBA MG09ACA18TE (Standardlaufwerke)`
     *
     * Output: `MG09ACA18TE`
     */
    private fun parseModel(model: String, manufacturer: String): String {
        val modelWithoutInfo = model.substringBefore("(").trim()
        return if (manufacturer.isEmpty()) {
            modelWithoutInfo
        } else {
            modelWithoutInfo.replace(manufacturer, "", ignoreCase = true).trim()
        }
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
        return trimmed.substringBefore(":").uppercase().takeIf { it.length == 1 } ?: ""
    }

    fun bytesToMB(bytes: Long): Double {
        if (bytes <= 0) return 0.0
        return bytes.toDouble() / (1024.0 * 1024.0)
    }
}

private fun Partition.applyPartition(
    disk: Disk,
    partitionCandidate: OshiImporter.PartitionCandidate,
    letter: String,
    partitionsUpdated: AtomicInteger
) {

    var changed = false

    val sizeMB = bytesToMB(partitionCandidate.sizeBytes)
    val usedMB = partitionCandidate.usedMB
    val fsType = partitionCandidate.fsType
    val label = partitionCandidate.label

    if (this.diskId != disk.id) {
        this.diskId = disk.id; changed = true
    }
    if (this.name != label && label.isNotEmpty()) {
        this.name = label; changed = true
    }
    if (this.sizeMB != sizeMB && sizeMB > 0) {
        this.sizeMB = sizeMB; changed = true
    }
    if (usedMB >= 0.0 && abs(usedMB - usedMB) > 0.0001) {
        this.usedMB = usedMB; changed = true
    }
    if (uuid.isBlank() && partitionCandidate.uuid.isNotEmpty()) {
        uuid = partitionCandidate.uuid; changed = true
    }
    if (this.letter.isBlank() && letter.isNotEmpty()) {
        this.letter = letter; changed = true
    }
    if (this.fsType.isBlank() && fsType.isNotEmpty()) {
        this.fsType = fsType; changed = true
    }
    if (changed) {
        DiskRepository.updatePartition(this); partitionsUpdated.incrementAndGet()
    }
    if (this.virtual && partitionCandidate.diskStore != null) {
        // If previously virtual but now we have hardware info, clear virtual flag
        this.virtual = false; changed = true
    }
    if (!this.virtual && partitionCandidate.diskStore == null) {
        // Partition lost hardware backing – set virtual
        this.virtual = true; changed = true
    }
}
