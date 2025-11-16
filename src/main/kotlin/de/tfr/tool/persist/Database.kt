package de.tfr.tool.persist

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

object Database {
    private var conn: Connection? = null
    @Volatile
    private var dbPathOverride: Path? = null

    fun connection(): Connection {
        if (conn == null || conn!!.isClosed) {
            val dbPath = getCurrentDbPath()
            Files.createDirectories(dbPath.parent)
            val url = "jdbc:sqlite:${dbPath.toAbsolutePath()}"
            conn = DriverManager.getConnection(url)
            conn!!.createStatement().use { st -> st.execute("PRAGMA foreign_keys = ON;") }
        }
        return conn!!
    }

    fun initSchema() {
        val sqlDisks = """
            CREATE TABLE IF NOT EXISTS disks (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              size_tb REAL NOT NULL DEFAULT 0,
              type TEXT NOT NULL,
              model TEXT NOT NULL DEFAULT '',
              tag TEXT NOT NULL DEFAULT ''
            );
        """.trimIndent()

        val sqlPartitions = """
            CREATE TABLE IF NOT EXISTS partitions (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              disk_id INTEGER NOT NULL,
              name TEXT NOT NULL,
              letter TEXT NOT NULL DEFAULT '',
              type TEXT NOT NULL,
              size_tb REAL NOT NULL DEFAULT 0,
              used_tb REAL NOT NULL DEFAULT 0,
              tags TEXT NOT NULL DEFAULT '',
              FOREIGN KEY(disk_id) REFERENCES disks(id) ON DELETE CASCADE
            );
        """.trimIndent()

        connection().createStatement().use { st ->
            st.execute(sqlDisks)
            st.execute(sqlPartitions)
        }

        // Migration: add missing columns
        ensureDiskColumns()
        ensurePartitionColumns()
    }

    /**
     * Deletes all data from the current database (all disks and partitions).
     * Returns the number of deleted disks and partitions.
     */
    fun clearAllData(): Pair<Int, Int> {
        val conn = connection()
        var disksCount = 0
        var partsCount = 0
        val auto = conn.autoCommit
        try {
            conn.autoCommit = false
            // Count before delete
            conn.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM partitions").use { rs -> if (rs.next()) partsCount = rs.getInt(1) }
            }
            conn.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM disks").use { rs -> if (rs.next()) disksCount = rs.getInt(1) }
            }

            // Delete – due to FK CASCADE deleting from disks would be enough; still clear both tables for safety
            conn.createStatement().use { st -> st.executeUpdate("DELETE FROM partitions") }
            conn.createStatement().use { st -> st.executeUpdate("DELETE FROM disks") }

            // Reset autoincrement (optional)
            try {
                conn.createStatement().use { st -> st.executeUpdate("DELETE FROM sqlite_sequence WHERE name IN ('disks','partitions')") }
            } catch (_: Exception) { /* not critical */ }

            conn.commit()
        } catch (ex: Exception) {
            try { conn.rollback() } catch (_: Exception) {}
            throw ex
        } finally {
            try { conn.autoCommit = auto } catch (_: Exception) {}
        }
        return disksCount to partsCount
    }

    private fun ensurePartitionColumns() {
        val conn = connection()
        val existing = mutableSetOf<String>()
        conn.createStatement().use { st ->
            val rs = st.executeQuery("PRAGMA table_info(partitions)")
            while (rs.next()) {
                existing += rs.getString("name").lowercase()
            }
        }

        if (!existing.contains("encrypted")) {
            conn.createStatement().use { st ->
                st.execute("ALTER TABLE partitions ADD COLUMN encrypted INTEGER NOT NULL DEFAULT 0")
            }
        }
        if (!existing.contains("cloud_backup")) {
            conn.createStatement().use { st ->
                st.execute("ALTER TABLE partitions ADD COLUMN cloud_backup INTEGER NOT NULL DEFAULT 0")
            }
        }
        if (!existing.contains("uuid")) {
            conn.createStatement().use { st ->
                st.execute("ALTER TABLE partitions ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
            }
        }
        if (!existing.contains("fs_type")) {
            conn.createStatement().use { st ->
                st.execute("ALTER TABLE partitions ADD COLUMN fs_type TEXT NOT NULL DEFAULT ''")
            }
        }
        if (!existing.contains("hidden")) {
            conn.createStatement().use { st ->
                st.execute("ALTER TABLE partitions ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    private fun ensureDiskColumns() {
        val conn = connection()
        val existing = mutableSetOf<String>()
        conn.createStatement().use { st ->
            val rs = st.executeQuery("PRAGMA table_info(disks)")
            while (rs.next()) {
                existing += rs.getString("name").lowercase()
            }
        }
        if (!existing.contains("manufacturer")) {
            conn.createStatement().use { st ->
                st.execute("ALTER TABLE disks ADD COLUMN manufacturer TEXT NOT NULL DEFAULT ''")
            }
        }
        if (!existing.contains("serial")) {
            conn.createStatement().use { st ->
                st.execute("ALTER TABLE disks ADD COLUMN serial TEXT NOT NULL DEFAULT ''")
            }
        }
        if (!existing.contains("hidden")) {
            conn.createStatement().use { st ->
                st.execute("ALTER TABLE disks ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    private fun defaultDbPath(): Path {
        val base = System.getenv("APPDATA")
        val dir = if (base != null && base.isNotBlank()) Path.of(base, "HardDriveManager") else Path.of(System.getProperty("user.home"), ".hard-drive-manager")
        return dir.resolve("hard-drive-manager.db")
    }

    @Synchronized
    fun setDatabaseFile(path: Path?) {
        val newPath = path?.toAbsolutePath()
        if (dbPathOverride == newPath) return
        // close existing connection so a new one will be opened lazily
        try {
            conn?.close()
        } catch (_: Exception) { }
        conn = null
        dbPathOverride = newPath
    }

    fun getCurrentDbPath(): Path = (dbPathOverride ?: defaultDbPath()).toAbsolutePath()
}
