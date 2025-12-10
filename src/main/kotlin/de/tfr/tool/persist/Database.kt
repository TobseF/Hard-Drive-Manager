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
              size_mb REAL NOT NULL DEFAULT 0,
              type TEXT NOT NULL,
              model TEXT NOT NULL DEFAULT '',
              tag TEXT NOT NULL DEFAULT '',
              comment TEXT NOT NULL DEFAULT ''
            );
        """.trimIndent()

        val sqlPartitions = """
            CREATE TABLE IF NOT EXISTS partitions (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              disk_id INTEGER NOT NULL,
              name TEXT NOT NULL,
              letter TEXT NOT NULL DEFAULT '',
              type TEXT NOT NULL,
              size_mb REAL NOT NULL DEFAULT 0,
              used_mb REAL NOT NULL DEFAULT 0,
              tags TEXT NOT NULL DEFAULT '',
              comment TEXT NOT NULL DEFAULT '',
              FOREIGN KEY(disk_id) REFERENCES disks(id) ON DELETE CASCADE
            );
        """.trimIndent()

        connection().createStatement().use { st ->
            st.execute(sqlDisks)
            st.execute(sqlPartitions)
        }

        // Migration: add missing columns and convert old TB columns to MB
        ensureDiskColumns()
        ensurePartitionColumns()
        migrateTBtoMB()
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
        if (!existing.contains("virtual")) {
            conn.createStatement().use { st ->
                st.execute("ALTER TABLE partitions ADD COLUMN virtual INTEGER NOT NULL DEFAULT 0")
            }
        }
        if (!existing.contains("comment")) {
            conn.createStatement().use { st ->
                st.execute("ALTER TABLE partitions ADD COLUMN comment TEXT NOT NULL DEFAULT ''")
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
        if (!existing.contains("comment")) {
            conn.createStatement().use { st ->
                st.execute("ALTER TABLE disks ADD COLUMN comment TEXT NOT NULL DEFAULT ''")
            }
        }
    }

    /**
     * Migrates old size_tb/used_tb columns to size_mb/used_mb (multiply by 1024).
     * Only performs migration if the old columns exist and new ones don't.
     */
    private fun migrateTBtoMB() {
        val conn = connection()

        // Check if old columns exist and new ones don't
        var diskHasOldColumn = false
        var diskHasNewColumn = false
        var partHasOldColumns = false
        var partHasNewColumns = false

        conn.createStatement().use { st ->
            val rs = st.executeQuery("PRAGMA table_info(disks)")
            while (rs.next()) {
                val colName = rs.getString("name").lowercase()
                if (colName == "size_tb") diskHasOldColumn = true
                if (colName == "size_mb") diskHasNewColumn = true
            }
        }

        conn.createStatement().use { st ->
            val rs = st.executeQuery("PRAGMA table_info(partitions)")
            while (rs.next()) {
                val colName = rs.getString("name").lowercase()
                if (colName == "size_tb" || colName == "used_tb") partHasOldColumns = true
                if (colName == "size_mb" || colName == "used_mb") partHasNewColumns = true
            }
        }

        // Migration: rename old columns to new and multiply values by 1024
        if (diskHasOldColumn && !diskHasNewColumn) {
            try {
                conn.createStatement().use { st ->
                    // Rename size_tb to size_mb and multiply by 1024
                    st.execute("ALTER TABLE disks RENAME COLUMN size_tb TO size_mb")
                    st.execute("UPDATE disks SET size_mb = size_mb * 1024.0")
                }
            } catch (ex: Exception) {
                // Column might already be renamed
            }
        }

        if (partHasOldColumns && !partHasNewColumns) {
            try {
                conn.createStatement().use { st ->
                    // Rename size_tb to size_mb and multiply by 1024
                    st.execute("ALTER TABLE partitions RENAME COLUMN size_tb TO size_mb")
                    st.execute("UPDATE partitions SET size_mb = size_mb * 1024.0")
                    // Rename used_tb to used_mb and multiply by 1024
                    st.execute("ALTER TABLE partitions RENAME COLUMN used_tb TO used_mb")
                    st.execute("UPDATE partitions SET used_mb = used_mb * 1024.0")
                }
            } catch (ex: Exception) {
                // Columns might already be renamed
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

    fun getCurrentDbPathAsString(): String = try {
        getCurrentDbPath().toString()
    } catch (e: Exception) {
        ""
    }
}
