package de.tfr.tool.quickinfo.partitions

import oshi.SystemInfo
import java.text.DecimalFormat

object OSHIDiskInfo {
    @JvmStatic
    fun main(args: Array<String>) {
        val si = SystemInfo()
        val hal = si.getHardware()
        val os = si.getOperatingSystem()

        val format = DecimalFormat("#.##")

        println("--- OSHI: Physical Disks & Partitions ---")

        val diskStores = hal.getDiskStores()
        if (diskStores.isEmpty()) {
            println("No physical disks found.")
        }

        for (disk in diskStores) {
            println("\nDisk: " + disk.getName())
            println("  Model: " + disk.getModel())
            println("  Serial number: " + disk.getSerial())
            System.out.printf("  Total size: %s GB%n", format.format(disk.getSize().toDouble() / (1024 * 1024 * 1024)))

            println("  Partitions:")
            val partitions = disk.getPartitions()
            if (partitions.isEmpty()) {
                println("    No partitions found.")
            } else {
                for (p in partitions) {
                    println("    Partition: " + p.getName())
                    println("      UUID: " + p.getUuid())
                    println("      Type: " + p.getType())
                    System.out.printf(
                        "      Size: %s GB%n",
                        format.format(p.getSize().toDouble() / (1024 * 1024 * 1024))
                    )


                    // To get used space we need to correlate file stores
                    // A partition (HWPartition) can correspond to a file store (OSFileStore)
                    // Here is an example of how one could derive used/free:
                    for (fs in os.getFileSystem().getFileStores()) {
                        // This is a simple heuristic and can be inaccurate on complex systems
                        if (fs.getUUID() == p.getUuid() || fs.getName() == p.getName()) {
                            System.out.printf("      Filesystem name: %s%n", fs.getName())
                            System.out.printf("      Filesystem type: %s%n", fs.getType())
                            System.out.printf(
                                "      Total space (FS): %s GB%n",
                                format.format(fs.getTotalSpace().toDouble() / (1024 * 1024 * 1024))
                            )
                            System.out.printf(
                                "      Usable space (FS): %s GB%n",
                                format.format(fs.getUsableSpace().toDouble() / (1024 * 1024 * 1024))
                            )
                            System.out.printf(
                                "      Used space (FS): %s GB%n",
                                format.format((fs.getTotalSpace() - fs.getUsableSpace()).toDouble() / (1024 * 1024 * 1024))
                            )
                            break
                        }
                    }
                }
            }
        }
    }
}