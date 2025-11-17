package de.tfr.tool.ui

import javafx.beans.property.SimpleObjectProperty
import java.text.MessageFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

object I18n {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    val languageProperty = SimpleObjectProperty(Language.DE)

    val currentLanguage: Language
        get() = languageProperty.get()

    private var bundle: ResourceBundle = loadBundle(Language.DE)

    fun setLanguage(lang: Language) {
        if (lang == currentLanguage) return
        languageProperty.set(lang)
        bundle = loadBundle(lang)
        listeners.forEach { it.invoke() }
    }

    fun addListener(listener: () -> Unit) { listeners += listener }
    fun removeListener(listener: () -> Unit) { listeners -= listener }

    private fun loadBundle(lang: Language): ResourceBundle {
        val base = "i18n.messages"
        val locale = Locale.forLanguageTag(lang.code)
        return ResourceBundle.getBundle(base, locale, ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_DEFAULT))
    }

    fun s(key: String, vararg args: Any?): String {
        val pattern = if (bundle.containsKey(key)) bundle.getString(key) else key
        return if (args.isEmpty()) pattern else MessageFormat(pattern).format(args)
    }
}
