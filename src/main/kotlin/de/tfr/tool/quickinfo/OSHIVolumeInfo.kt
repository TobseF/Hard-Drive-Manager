package de.tfr.tool.quickinfo

import oshi.SystemInfo
import java.text.DecimalFormat

object OSHIVolumeInfo {
    @JvmStatic
    fun main(args: Array<String>) {
        val si = SystemInfo()
        val os = si.operatingSystem

        val format = DecimalFormat("#.##")

        println("--- OSHI: Filesystems (Volumes) ---")

        // Retrieve the list of all mounted file systems
        for (fs in os.fileSystem.fileStores) {
            // fs.name is the filesystem/volume name
            // On Windows this is the volume label or name

            println("Name:             " + fs.name)
            println("Label:            " + fs.label)

            // fs.mount is the drive letter or mount point
            println("  Mount point:     " + fs.mount)

            println("  Type:            " + fs.type)
            println("  UUID:            " + fs.uuid)
            System.out.printf(
                "  Total size:      %s GB%n",
                format.format(fs.totalSpace.toDouble() / (1024 * 1024 * 1024))
            )
            System.out.printf(
                "  Available:       %s GB%n",
                format.format(fs.usableSpace.toDouble() / (1024 * 1024 * 1024))
            )


            // Calculate used space
            val usedSpace = fs.totalSpace - fs.usableSpace
            System.out.printf("  Used space:      %s GB%n", format.format(usedSpace.toDouble() / (1024 * 1024 * 1024)))
            println("----------------------------------------")
        }
    }
}