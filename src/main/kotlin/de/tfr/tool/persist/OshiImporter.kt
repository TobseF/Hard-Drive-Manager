package de.tfr.tool.persist

import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import de.tfr.tool.model.percentOf
import oshi.SystemInfo
import java.util.concurrent.atomic.AtomicInteger

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
        val si = SystemInfo()
        val hw = si.hardware
        val os = si.operatingSystem

        val existing = DiskRepository.loadAll()
        val diskBySerial = existing.associateBy { it.serial.trim().lowercase() }.toMutableMap()
        val disksByModelSize = existing.associateBy { (it.model.trim().lowercase()) + "|" + it.sizeTB.toString() }.toMutableMap()

        // Map Partitionen nach UUID und Laufwerksbuchstabe
        val partByUuid = existing.flatMap { it.partitions }.associateBy { it.uuid.trim().lowercase() }.toMutableMap()
        val partByLetter = existing.flatMap { it.partitions }.associateBy { it.letter.trim().lowercase() }.toMutableMap()

        val du = AtomicInteger(0)
        val di = AtomicInteger(0)
        val pu = AtomicInteger(0)
        val pi = AtomicInteger(0)

        // Dateien-/Volume-Infos (für Partitionen)
        val fs = os.fileSystem
        val stores = fs.fileStores
        // Map nach UUID und Mount
        val storeByUuid = stores.associateBy { (it.uuid ?: "").trim().lowercase() }
        val storeByMount = stores.associateBy { (it.mount ?: it.name ?: "").trim().lowercase() }

        // Disks auslesen
        val oshiDisks = hw.diskStores
        oshiDisks.forEach { dstore ->
            val serial = (dstore.serial ?: "").trim()
            val model = (dstore.model ?: "").trim()
            val sizeTB = bytesToTB(dstore.size)
            val manufacturer = guessManufacturer(model)
            val type = guessType(model)

            val keySerial = serial.lowercase()
            var disk: Disk? = if (keySerial.isNotEmpty()) diskBySerial[keySerial] else null
            if (disk == null) {
                disk = disksByModelSize[model.lowercase() + "|" + sizeTB.toString()]
            }

            if (disk == null) {
                // create new
                val newDisk = Disk().apply {
                    name = if (model.isNotEmpty()) model else "Disk ${dstore.name}"
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
                di.incrementAndGet()
            } else {
                // complement/update
                var changed = false
                if (disk.sizeTB != sizeTB && sizeTB > 0) { disk.sizeTB = sizeTB; changed = true }
                if (disk.model.isBlank() && model.isNotEmpty()) { disk.model = model; changed = true }
                if (disk.manufacturer.isBlank() && manufacturer.isNotEmpty()) { disk.manufacturer = manufacturer; changed = true }
                if (disk.serial.isBlank() && serial.isNotEmpty()) { disk.serial = serial; changed = true }
                if (disk.type.isBlank() && type.isNotEmpty()) { disk.type = type; changed = true }
                if (changed) { DiskRepository.updateDisk(disk); du.incrementAndGet() }
            }

            // Map partitions
            dstore.partitions.forEach { part ->
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
                if (store != null) {
                    usedTB = bytesToTB(store.totalSpace - store.usableSpace)
                }

                val keyUuid = uuid.lowercase()
                var p: Partition? = if (keyUuid.isNotEmpty()) partByUuid[keyUuid] else null
                if (p == null && mount.isNotEmpty()) {
                    val letter = extractWindowsLetter(mount)
                    if (letter.isNotEmpty()) p = partByLetter[letter.lowercase()]
                }

                if (p == null) {
                    val outerUsed = usedTB
                    val outerUuid = uuid
                    val fsTypeStr = store?.type ?: ""
                    val newP = Partition().apply {
                        this.diskId = disk!!.id
                        this.name = buildPartitionName(mount)
                        this.letter = extractWindowsLetter(mount)
                        this.type = "Partition"
                        this.sizeTB = pSizeTB
                        this.usedTB = outerUsed
                        this.uuid = outerUuid
                        this.fsType = fsTypeStr
                        this.tags = ""
                    }
                    val pid = DiskRepository.insertPartition(newP)
                    newP.id = pid
                    disk!!.partitions += newP
                    if (outerUuid.isNotEmpty()) partByUuid[outerUuid.lowercase()] = newP
                    if (newP.letter.isNotBlank()) partByLetter[newP.letter.lowercase()] = newP
                    pi.incrementAndGet()
                } else {
                    var changed = false
                    if (p.diskId != disk!!.id) { p.diskId = disk.id; changed = true }
                    if (p.sizeTB != pSizeTB && pSizeTB > 0) { p.sizeTB = pSizeTB; changed = true }
                    if (usedTB >= 0.0 && Math.abs(p.usedTB - usedTB) > 0.0001) { p.usedTB = usedTB; changed = true }
                    if (p.uuid.isBlank() && uuid.isNotEmpty()) { p.uuid = uuid; changed = true }
                    val letter = extractWindowsLetter(mount)
                    if (p.letter.isBlank() && letter.isNotEmpty()) { p.letter = letter; changed = true }
                    val fsType = store?.type ?: ""
                    if (p.fsType.isBlank() && fsType.isNotEmpty()) { p.fsType = fsType; changed = true }
                    if (changed) { DiskRepository.updatePartition(p); pu.incrementAndGet() }
                }
            }
        }

        return Result(du.get(), di.get(), pu.get(), pi.get())
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
        return if (trimmed.length >= 2 && trimmed[1] == ':') trimmed.substring(0, 1).uppercase() else ""
    }

    private fun buildPartitionName(mount: String): String {
        val letter = extractWindowsLetter(mount)
        return if (letter.isNotEmpty()) "$letter:" else mount.ifBlank { "Partition" }
    }
}
