package de.tfr.tool.persist

import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import de.tfr.tool.model.PartitionType
import de.tfr.tool.model.SampleDataRepository
import java.sql.ResultSet

object DiskRepository {
    fun seedIfEmpty() {
        val count = Database.connection().createStatement().use { st ->
            val rs = st.executeQuery("SELECT COUNT(*) FROM disks")
            rs.next()
            rs.getInt(1)
        }
        if (count == 0) {
            val samples = SampleDataRepository.sampleDisks()
            samples.forEach { d ->
                val diskId = insertDisk(d)
                d.partitions.forEach { p ->
                    p.diskId = diskId
                    // Default: encrypted = true if type is EncryptedContainer
                    try {
                        p.encrypted = p.type == PartitionType.EncryptedContainer.name
                        // Set virtual flag for encrypted containers as initial heuristic
                        if (p.type == PartitionType.EncryptedContainer.name) p.virtual = true
                    } catch (_: Exception) {}
                    insertPartition(p)
                }
            }
        }
    }

    fun loadAll(): MutableList<Disk> {
        val conn = Database.connection()
        val disks = mutableListOf<Disk>()
        conn.createStatement().use { st ->
            val rs =
                st.executeQuery("SELECT id, name, size_mb, type, model, manufacturer, serial, tag, hidden, comment FROM disks ORDER BY name ASC")
            while (rs.next()) {
                disks += rs.toDisk()
            }
        }
        // partitions
        conn.createStatement().use { st ->
            val rs =
                st.executeQuery("SELECT id, disk_id, name, letter, type, size_mb, used_mb, tags, encrypted, cloud_backup, uuid, fs_type, hidden, virtual, comment FROM partitions ORDER BY id ASC")
            while (rs.next()) {
                val p = rs.toPartition()
                disks.find { it.id == p.diskId }?.partitions?.add(p)
            }
        }
        return disks
    }

    fun insertDisk(d: Disk): Long {
        val sql =
            "INSERT INTO disks(name, size_mb, type, model, manufacturer, serial, tag, hidden, comment) VALUES(?,?,?,?,?,?,?,?,?)"
        Database.connection().prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setString(1, d.name)
            ps.setDouble(2, d.sizeMB)
            ps.setString(3, d.type)
            ps.setString(4, d.model)
            ps.setString(5, d.manufacturer)
            ps.setString(6, d.serial)
            ps.setString(7, d.tag)
            ps.setInt(8, if (d.hidden) 1 else 0)
            ps.setString(9, d.comment)
            ps.executeUpdate()
            ps.generatedKeys.use { keys ->
                return if (keys.next()) keys.getLong(1) else 0L
            }
        }
    }

    fun updateDisk(d: Disk) {
        val sql =
            "UPDATE disks SET name=?, size_mb=?, type=?, model=?, manufacturer=?, serial=?, tag=?, hidden=?, comment=? WHERE id=?"
        Database.connection().prepareStatement(sql).use { ps ->
            ps.setString(1, d.name)
            ps.setDouble(2, d.sizeMB)
            ps.setString(3, d.type)
            ps.setString(4, d.model)
            ps.setString(5, d.manufacturer)
            ps.setString(6, d.serial)
            ps.setString(7, d.tag)
            ps.setInt(8, if (d.hidden) 1 else 0)
            ps.setString(9, d.comment)
            ps.setLong(10, d.id)
            ps.executeUpdate()
        }
    }

    fun deleteDisk(id: Long) {
        Database.connection().prepareStatement("DELETE FROM disks WHERE id=?").use { ps ->
            ps.setLong(1, id)
            ps.executeUpdate()
        }
    }

    fun insertPartition(p: Partition): Long {
        val sql =
            "INSERT INTO partitions(disk_id, name, letter, type, size_mb, used_mb, tags, encrypted, cloud_backup, uuid, fs_type, hidden, virtual, comment) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        Database.connection().prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setLong(1, p.diskId)
            ps.setString(2, p.name)
            ps.setString(3, p.letter)
            ps.setString(4, p.type)
            ps.setDouble(5, p.sizeMB)
            ps.setDouble(6, p.usedMB)
            ps.setString(7, p.tags)
            ps.setInt(8, if (p.encrypted) 1 else 0)
            ps.setInt(9, if (p.cloudBackup) 1 else 0)
            ps.setString(10, p.uuid)
            ps.setString(11, p.fsType)
            ps.setInt(12, if (p.hidden) 1 else 0)
            ps.setInt(13, if (p.virtual) 1 else 0)
            ps.setString(14, p.comment)
            ps.executeUpdate()
            ps.generatedKeys.use { keys ->
                return if (keys.next()) keys.getLong(1) else 0L
            }
        }
    }

    fun updatePartition(p: Partition) {
        val sql =
            "UPDATE partitions SET disk_id=?, name=?, letter=?, type=?, size_mb=?, used_mb=?, tags=?, encrypted=?, cloud_backup=?, uuid=?, fs_type=?, hidden=?, virtual=?, comment=? WHERE id=?"
        Database.connection().prepareStatement(sql).use { ps ->
            ps.setLong(1, p.diskId)
            ps.setString(2, p.name)
            ps.setString(3, p.letter)
            ps.setString(4, p.type)
            ps.setDouble(5, p.sizeMB)
            ps.setDouble(6, p.usedMB)
            ps.setString(7, p.tags)
            ps.setInt(8, if (p.encrypted) 1 else 0)
            ps.setInt(9, if (p.cloudBackup) 1 else 0)
            ps.setString(10, p.uuid)
            ps.setString(11, p.fsType)
            ps.setInt(12, if (p.hidden) 1 else 0)
            ps.setInt(13, if (p.virtual) 1 else 0)
            ps.setString(14, p.comment)
            ps.setLong(15, p.id)
            ps.executeUpdate()
        }
    }

    fun deletePartition(id: Long) {
        Database.connection().prepareStatement("DELETE FROM partitions WHERE id=?").use { ps ->
            ps.setLong(1, id)
            ps.executeUpdate()
        }
    }

    private fun ResultSet.toDisk(): Disk = Disk().apply {
        id = getLong("id")
        name = getString("name")
        sizeMB = getDouble("size_mb")
        type = getString("type")
        model = getString("model")
        try { manufacturer = getString("manufacturer") } catch (_: Exception) {}
        try { serial = getString("serial") } catch (_: Exception) {}
        tag = getString("tag")
        try { hidden = getInt("hidden") != 0 } catch (_: Exception) {}
        try {
            comment = getString("comment")
        } catch (_: Exception) {
        }
    }

    private fun ResultSet.toPartition(): Partition = Partition().apply {
        id = getLong("id")
        diskId = getLong("disk_id")
        name = getString("name")
        letter = getString("letter")
        type = getString("type")
        sizeMB = getDouble("size_mb")
        usedMB = getDouble("used_mb")
        tags = getString("tags")
        // map optional columns if present
        try { encrypted = getInt("encrypted") != 0 } catch (_: Exception) {}
        try { cloudBackup = getInt("cloud_backup") != 0 } catch (_: Exception) {}
        try { uuid = getString("uuid") } catch (_: Exception) {}
        try { fsType = getString("fs_type") } catch (_: Exception) {}
        try { hidden = getInt("hidden") != 0 } catch (_: Exception) {}
        try {
            virtual = getInt("virtual") != 0
        } catch (_: Exception) {
        }
        try {
            comment = getString("comment")
        } catch (_: Exception) {
        }
    }
}
