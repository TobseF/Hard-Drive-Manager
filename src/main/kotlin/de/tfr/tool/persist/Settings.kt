package de.tfr.tool.persist

import de.tfr.tool.de.tfr.tool.ui.i18n.Language
import de.tfr.tool.model.DisplayUnit
import de.tfr.tool.model.SortDirection
import de.tfr.tool.ui.Theme
import java.util.prefs.Preferences
import kotlin.reflect.KProperty

/**
 *  Typesafe settings provider which stores all values in the Windows registry.
 */
object Settings {

    /**
     * Saves the settings in the Windows registry.
     */
    private val prefs = Preferences.userRoot().node("de/tfr/tool/harddrivemanager")

    var equalCardHeights by BooleanProp("equalCardHeights")
    var fixedCardHeightEnabled by BooleanProp("fixedCardHeightEnabled")
    var fixedCardHeightPx by DoubleProp("fixedCardHeightPx", 220.0)
    var theme by EnumProp("theme", Theme::fromString, Theme.LIGHT)
    var language by EnumProp("language", Language::fromString, Language.DE)
    var displayUnit by EnumProp("displayUnit", DisplayUnit::fromString, DisplayUnit.TB)
    var dbPath by StringProp("db.path", trim = true)

    object Table {
        var showHidden by BooleanProp("table.showHidden")
        var sortField by StringProp("table.sortField", "name")
        var sortDirection by EnumProp("table.sortDirection", SortDirection::fromString, SortDirection.ASCENDING)

        // Column visibility settings
        var showName by BooleanProp("table.col.showName", true)
        var showType by BooleanProp("table.col.showType", true)
        var showLetter by BooleanProp("table.col.showLetter", true)
        var showSize by BooleanProp("table.col.showSize", true)
        var showUsed by BooleanProp("table.col.showUsed", true)
        var showFree by BooleanProp("table.col.showFree", true)
        var showPercentText by BooleanProp("table.col.showPercentText", true)
        var showPartOfDiskBar by BooleanProp("table.col.showPartOfDiskBar", true)
        var showBar by BooleanProp("table.col.showBar", true)
        var showTag by BooleanProp("table.col.showTag", true)
        var showModel by BooleanProp("table.col.showModel", false)
        var showManufacturer by BooleanProp("table.col.showManufacturer", false)
        var showSerial by BooleanProp("table.col.showSerial", false)
        var showUuid by BooleanProp("table.col.showUuid", false)
        var showFsType by BooleanProp("table.col.showFsType", false)
        var showEncrypted by BooleanProp("table.col.showEncrypted", true)
        var showCloud by BooleanProp("table.col.showCloud", true)
        var showVirtual by BooleanProp("table.col.showVirtual", false)
        var showHiddenCol by BooleanProp("table.col.showHidden", true)
    }

    class StringProp(private val key: String, private val defaultValue: String = "", private val trim: Boolean = true) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): String {
            return prefs.get(key, defaultValue)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            if (trim) {
                prefs.put(key, value.trim())
            } else {
                prefs.put(key, value)
            }
        }
    }

    class BooleanProp(private val key: String, private val defaultValue: Boolean = false) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Boolean {
            return prefs.getBoolean(key, defaultValue)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            prefs.putBoolean(key, value)
        }
    }

    class DoubleProp(private val key: String, private val defaultValue: Double = 0.0) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Double {
            return prefs.getDouble(key, defaultValue)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Double) {
            prefs.putDouble(key, value)
        }
    }

    class EnumProp<T : Enum<T>>(private val key: String, val getter: (String) -> T, private val defaultValue: T) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
            val savedName = prefs.get(key, null) ?: return defaultValue
            return getter.invoke(savedName)
        }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            prefs.put(key, value.name)
        }
    }
}