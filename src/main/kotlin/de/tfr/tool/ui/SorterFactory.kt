package de.tfr.tool.ui

import de.tfr.tool.model.Disk
import de.tfr.tool.model.Partition
import de.tfr.tool.model.SortDirection

object SorterFactory {
    fun getSortComparator(fieldName: String, direction: SortDirection): Comparator<Any> {
        val baseComparator = Comparator<Any> { o1, o2 ->
            when (fieldName) {
                "name" -> {
                    val name1 = when (o1) {
                        is Disk -> o1.name
                        is Partition -> o1.name
                        else -> ""
                    }
                    val name2 = when (o2) {
                        is Disk -> o2.name
                        is Partition -> o2.name
                        else -> ""
                    }
                    name1.compareTo(name2)
                }

                "type" -> {
                    val type1 = when (o1) {
                        is Disk -> o1.type
                        is Partition -> o1.type
                        else -> ""
                    }
                    val type2 = when (o2) {
                        is Disk -> o2.type
                        is Partition -> o2.type
                        else -> ""
                    }
                    type1.compareTo(type2)
                }

                "size" -> {
                    val size1 = when (o1) {
                        is Disk -> o1.sizeMB
                        is Partition -> o1.sizeMB
                        else -> 0.0
                    }
                    val size2 = when (o2) {
                        is Disk -> o2.sizeMB
                        is Partition -> o2.sizeMB
                        else -> 0.0
                    }
                    size1.compareTo(size2)
                }

                "used" -> {
                    val used1 = when (o1) {
                        is Disk -> o1.usedMB
                        is Partition -> o1.usedMB
                        else -> 0.0
                    }
                    val used2 = when (o2) {
                        is Disk -> o2.usedMB
                        is Partition -> o2.usedMB
                        else -> 0.0
                    }
                    used1.compareTo(used2)
                }

                "free" -> {
                    val free1 = when (o1) {
                        is Disk -> (o1.sizeMB - o1.usedMB).coerceAtLeast(0.0)
                        is Partition -> (o1.sizeMB - o1.usedMB).coerceAtLeast(0.0)
                        else -> 0.0
                    }
                    val free2 = when (o2) {
                        is Disk -> (o2.sizeMB - o2.usedMB).coerceAtLeast(0.0)
                        is Partition -> (o2.sizeMB - o2.usedMB).coerceAtLeast(0.0)
                        else -> 0.0
                    }
                    free1.compareTo(free2)
                }

                "letter" -> {
                    val letter1 = (o1 as? Partition)?.letter ?: ""
                    val letter2 = (o2 as? Partition)?.letter ?: ""
                    letter1.compareTo(letter2)
                }

                "tags" -> {
                    val tags1 = when (o1) {
                        is Disk -> o1.tag
                        is Partition -> o1.tags
                        else -> ""
                    }
                    val tags2 = when (o2) {
                        is Disk -> o2.tag
                        is Partition -> o2.tags
                        else -> ""
                    }
                    tags1.compareTo(tags2)
                }

                else -> 0
            }
        }

        return if (direction == SortDirection.DESCENDING) {
            baseComparator.reversed()
        } else {
            baseComparator
        }
    }
}