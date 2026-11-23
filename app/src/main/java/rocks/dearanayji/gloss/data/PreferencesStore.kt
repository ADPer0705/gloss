package rocks.dearanayji.gloss.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

data class HistoryItem(
    val sourceText: String,
    val translatedText: String,
    val sourceCode: String,
    val targetCode: String,
    val timestampMillis: Long
)

class PreferencesStore(context: Context) {

    private val prefs = context.getSharedPreferences("gloss_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    var sourceLangCode: String
        get() = prefs.getString(KEY_SOURCE_LANG, LanguageCatalog.DEFAULT_SOURCE_CODE)
            ?: LanguageCatalog.DEFAULT_SOURCE_CODE
        set(value) {
            prefs.edit().putString(KEY_SOURCE_LANG, value).apply()
            recordRecentLanguage(value)
        }

    var targetLangCode: String
        get() = prefs.getString(KEY_TARGET_LANG, LanguageCatalog.DEFAULT_TARGET_CODE)
            ?: LanguageCatalog.DEFAULT_TARGET_CODE
        set(value) {
            prefs.edit().putString(KEY_TARGET_LANG, value).apply()
            recordRecentLanguage(value)
        }

    var themeMode: ThemeMode
        get() {
            val raw = prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name)
            return runCatching { ThemeMode.valueOf(raw ?: ThemeMode.DARK.name) }
                .getOrDefault(ThemeMode.DARK)
        }
        set(value) {
            prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
        }

    fun getRecentLanguageCodes(): List<String> {
        val json = prefs.getString(KEY_RECENT_LANGS, null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return runCatching { gson.fromJson<List<String>>(json, type) }.getOrNull().orEmpty()
    }

    fun recordRecentLanguage(code: String) {
        val updated = buildList {
            add(code)
            getRecentLanguageCodes().forEach { existing ->
                if (existing != code && size < MAX_RECENT_LANGS) add(existing)
            }
        }
        prefs.edit().putString(KEY_RECENT_LANGS, gson.toJson(updated)).apply()
    }

    fun getHistory(): List<HistoryItem> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val type = object : TypeToken<List<HistoryItem>>() {}.type
        return runCatching { gson.fromJson<List<HistoryItem>>(json, type) }.getOrNull().orEmpty()
    }

    fun addHistoryItem(item: HistoryItem): List<HistoryItem> {
        val current = getHistory()
        val deduped = current.filterNot {
            it.sourceText == item.sourceText &&
                it.sourceCode == item.sourceCode &&
                it.targetCode == item.targetCode
        }
        val updated = (listOf(item) + deduped).take(MAX_HISTORY_ITEMS)
        prefs.edit().putString(KEY_HISTORY, gson.toJson(updated)).apply()
        return updated
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    companion object {
        private const val KEY_SOURCE_LANG = "source_lang"
        private const val KEY_TARGET_LANG = "target_lang"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_RECENT_LANGS = "recent_langs"
        private const val KEY_HISTORY = "phrase_history"
        private const val MAX_RECENT_LANGS = 8
        private const val MAX_HISTORY_ITEMS = 30
    }
}
