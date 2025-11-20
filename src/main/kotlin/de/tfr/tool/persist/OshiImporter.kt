package de.tfr.tool.persist

import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
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

        // 3. For each partition candidate resolve/create its disk, then merge partition
        partitionCandidates.forEach { cand ->
            // Resolve disk
            val disk: Disk = if (cand.diskStore != null) {
                val diskStore = cand.diskStore
                val serial = (diskStore.serial ?: "").trim()
                val modelSystemInfo = (diskStore.model ?: "").trim()
                val manufacturer = guessManufacturer(modelSystemInfo)
                val model = parseModel(modelSystemInfo, manufacturer)
                val sizeMB = bytesToMB(diskStore.size)
                val type = guessType(model)
                val keySerial = serial.lowercase()
                var d: Disk? = if (keySerial.isNotEmpty()) diskBySerial[keySerial] else null
                if (d == null) {
                    d = disksByModelSize[model.lowercase() + "|" + sizeMB.toString()]
                }
                val generatedDriveName = listOf(manufacturer, type).filter { it.isNotBlank() }.joinToString(" ")
                    .ifBlank { model.ifBlank { serial }.ifBlank { "Disk" } }
                if (d == null) {
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
                    if (d.sizeMB != sizeMB && sizeMB > 0) {
                        d.sizeMB = sizeMB; changed = true
                    }
                    if (d.model.isBlank() && model.isNotEmpty()) {
                        d.model = model; changed = true
                    }
                    if (d.manufacturer.isBlank() && manufacturer.isNotEmpty()) {
                        d.manufacturer = manufacturer; changed = true
                    }
                    if (d.serial.isBlank() && serial.isNotEmpty()) {
                        d.serial = serial; changed = true
                    }
                    if (d.type.isBlank() && type.isNotEmpty()) {
                        d.type = type; changed = true
                    }
                    if (d.name.isBlank()) {
                        d.name = generatedDriveName; changed = true
                    }
                    if (changed) {
                        DiskRepository.updateDisk(d); disksUpdated.incrementAndGet()
                    }
                    d
                }
            } else {
                // No disk info -> use Unknown Disk placeholder
                ensureUnknownDisk()
            }

            // Partition merge/create
            val uuidKey = cand.uuid.trim().lowercase()
            val mountPoint = cand.mountPoint.ifBlank { cand.mountRaw }
            val letter = extractWindowsLetter(mountPoint)
            var partitionFromDB: Partition? = if (uuidKey.isNotEmpty()) partitionByUuid[uuidKey] else null
            if (partitionFromDB == null && letter.isNotEmpty()) {
                partitionFromDB = partitionByLetter[letter.lowercase()]
            }

            val sizeMB = bytesToMB(cand.sizeBytes)
            val usedMB = cand.usedMB
            val fsType = cand.fsType
            val label = cand.label

            if (partitionFromDB == null) {
                val newPartition = Partition().apply {
                    this.diskId = disk.id
                    this.name = label.ifBlank { if (letter.isNotEmpty()) "$letter" else "Unknown" }
                    this.letter = letter
                    this.type = "Partition"
                    this.sizeMB = sizeMB
                    this.usedMB = usedMB
                    this.uuid = cand.uuid
                    this.fsType = fsType
                    this.tags = ""
                    this.virtual = cand.diskStore == null
                }
                val pid = DiskRepository.insertPartition(newPartition)
                newPartition.id = pid
                disk.partitions += newPartition
                if (cand.uuid.isNotBlank()) partitionByUuid[cand.uuid.lowercase()] = newPartition
                if (newPartition.letter.isNotBlank()) partitionByLetter[newPartition.letter.lowercase()] = newPartition
                partitionsInserted.incrementAndGet()
            } else {
                var changed = false
                if (partitionFromDB.diskId != disk.id) {
                    partitionFromDB.diskId = disk.id; changed = true
                }
                if (partitionFromDB.name != label && label.isNotEmpty()) {
                    partitionFromDB.name = label; changed = true
                }
                if (partitionFromDB.sizeMB != sizeMB && sizeMB > 0) {
                    partitionFromDB.sizeMB = sizeMB; changed = true
                }
                if (usedMB >= 0.0 && abs(partitionFromDB.usedMB - usedMB) > 0.0001) {
                    partitionFromDB.usedMB = usedMB; changed = true
                }
                if (partitionFromDB.uuid.isBlank() && cand.uuid.isNotEmpty()) {
                    partitionFromDB.uuid = cand.uuid; changed = true
                }
                if (partitionFromDB.letter.isBlank() && letter.isNotEmpty()) {
                    partitionFromDB.letter = letter; changed = true
                }
                if (partitionFromDB.fsType.isBlank() && fsType.isNotEmpty()) {
                    partitionFromDB.fsType = fsType; changed = true
                }
                if (changed) {
                    DiskRepository.updatePartition(partitionFromDB); partitionsUpdated.incrementAndGet()
                }
                if (partitionFromDB.virtual && cand.diskStore != null) {
                    // If previously virtual but now we have hardware info, clear virtual flag
                    partitionFromDB.virtual = false; changed = true
                }
                if (!partitionFromDB.virtual && cand.diskStore == null) {
                    // Partition lost hardware backing – set virtual
                    partitionFromDB.virtual = true; changed = true
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

    private fun bytesToMB(bytes: Long): Double {
        if (bytes <= 0) return 0.0
        return bytes.toDouble() / (1024.0 * 1024.0)
    }
}
