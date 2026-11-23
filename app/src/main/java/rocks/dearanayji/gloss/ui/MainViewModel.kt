package rocks.dearanayji.gloss.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rocks.dearanayji.gloss.BuildConfig
import rocks.dearanayji.gloss.GlossApplication
import rocks.dearanayji.gloss.data.HistoryItem
import rocks.dearanayji.gloss.data.LanguageCatalog
import rocks.dearanayji.gloss.data.ModelState
import rocks.dearanayji.gloss.data.ThemeMode
import rocks.dearanayji.gloss.engine.WordSegmenter
import rocks.dearanayji.gloss.update.AppUpdateManager
import rocks.dearanayji.gloss.update.ReleaseInfo
import java.io.File

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object UpToDate : AppUpdateState
    data class Available(val release: ReleaseInfo) : AppUpdateState
    data class Downloading(
        val release: ReleaseInfo,
        val fraction: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : AppUpdateState
    data class ReadyToInstall(val release: ReleaseInfo, val apkFile: File) : AppUpdateState
    data class Error(val message: String) : AppUpdateState
}

enum class WordOrigin {
    SOURCE,
    TRANSLATION
}

data class SelectedWordGloss(
    val wordIndex: Int,
    val origin: WordOrigin,
    val word: String,
    val fromLangCode: String,
    val toLangCode: String,
    val meaning: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

data class GlossUiState(
    val modelState: ModelState = ModelState.NotDownloaded,
    val sourceCode: String = LanguageCatalog.DEFAULT_SOURCE_CODE,
    val targetCode: String = LanguageCatalog.DEFAULT_TARGET_CODE,
    val inputText: String = "",
    val translatedText: String = "",
    val isTranslating: Boolean = false,
    val translationError: String? = null,
    val sourceTokens: List<WordSegmenter.TextToken> = emptyList(),
    val translatedTokens: List<WordSegmenter.TextToken> = emptyList(),
    val selectedWord: SelectedWordGloss? = null,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val recentLangCodes: List<String> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val installedSizeBytes: Long = 0L,
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val updateState: AppUpdateState = AppUpdateState.Idle,
    val updateBannerDismissed: Boolean = false,
    val totalGithubDownloads: Long = -1L,
    val latestReleaseDownloads: Long = -1L
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GlossApplication
    private val prefs = app.preferencesStore
    private val repo = app.engineRepository

    private data class DraftState(
        val sourceCode: String,
        val targetCode: String,
        val inputText: String,
        val translatedText: String,
        val isTranslating: Boolean,
        val translationError: String?,
        val sourceTokens: List<WordSegmenter.TextToken>,
        val translatedTokens: List<WordSegmenter.TextToken>,
        val selectedWord: SelectedWordGloss?,
        val themeMode: ThemeMode,
        val recentLangCodes: List<String>,
        val history: List<HistoryItem>,
        val updateState: AppUpdateState,
        val updateBannerDismissed: Boolean,
        val totalGithubDownloads: Long,
        val latestReleaseDownloads: Long
    )

    private val draft = MutableStateFlow(
        DraftState(
            sourceCode = prefs.sourceLangCode,
            targetCode = prefs.targetLangCode,
            inputText = "",
            translatedText = "",
            isTranslating = false,
            translationError = null,
            sourceTokens = emptyList(),
            translatedTokens = emptyList(),
            selectedWord = null,
            themeMode = prefs.themeMode,
            recentLangCodes = prefs.getRecentLanguageCodes(),
            history = prefs.getHistory(),
            updateState = AppUpdateState.Idle,
            updateBannerDismissed = false,
            totalGithubDownloads = prefs.cachedTotalGithubDownloads,
            latestReleaseDownloads = prefs.cachedLatestDownloads
        )
    )

    val uiState: StateFlow<GlossUiState> = combine(repo.modelState, draft) { modelState, d ->
        GlossUiState(
            modelState = modelState,
            sourceCode = d.sourceCode,
            targetCode = d.targetCode,
            inputText = d.inputText,
            translatedText = d.translatedText,
            isTranslating = d.isTranslating,
            translationError = d.translationError,
            sourceTokens = d.sourceTokens,
            translatedTokens = d.translatedTokens,
            selectedWord = d.selectedWord,
            themeMode = d.themeMode,
            recentLangCodes = d.recentLangCodes,
            history = d.history,
            installedSizeBytes = repo.installedSizeBytes(),
            currentVersionName = BuildConfig.VERSION_NAME,
            updateState = d.updateState,
            updateBannerDismissed = d.updateBannerDismissed,
            totalGithubDownloads = d.totalGithubDownloads,
            latestReleaseDownloads = d.latestReleaseDownloads
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GlossUiState(
            modelState = repo.modelState.value,
            sourceCode = draft.value.sourceCode,
            targetCode = draft.value.targetCode,
            themeMode = draft.value.themeMode,
            recentLangCodes = draft.value.recentLangCodes,
            history = draft.value.history,
            installedSizeBytes = repo.installedSizeBytes(),
            currentVersionName = BuildConfig.VERSION_NAME,
            totalGithubDownloads = draft.value.totalGithubDownloads,
            latestReleaseDownloads = draft.value.latestReleaseDownloads
        )
    )

    private var phraseJob: Job? = null
    private var wordJob: Job? = null
    private var updateJob: Job? = null

    init {
        viewModelScope.launch {
            repo.modelState.collect { state ->
                if (state is ModelState.Ready && draft.value.inputText.isNotBlank() &&
                    draft.value.translatedText.isBlank()
                ) {
                    scheduleTranslation(immediate = true)
                }
            }
        }
        val dayMillis = 24L * 60L * 60L * 1000L
        if (System.currentTimeMillis() - prefs.lastUpdateCheckMillis >= dayMillis && isOnline()) {
            checkForUpdatesInternal(silent = true)
        }
    }

    fun startDownload() {
        repo.startDownload()
    }

    fun cancelDownload() {
        repo.cancelDownload()
    }

    fun retryLoad() {
        repo.ensureLoadedAsync()
    }

    fun deleteModel() {
        viewModelScope.launch {
            repo.deleteModel()
            draft.update {
                it.copy(
                    translatedText = "",
                    translatedTokens = emptyList(),
                    isTranslating = false,
                    selectedWord = null
                )
            }
        }
    }

    fun onInputChanged(newText: String) {
        val tokens = WordSegmenter.tokenize(newText)
        draft.update {
            it.copy(
                inputText = newText,
                sourceTokens = tokens,
                selectedWord = null,
                translationError = null
            )
        }
        if (newText.isBlank()) {
            phraseJob?.cancel()
            draft.update {
                it.copy(
                    translatedText = "",
                    translatedTokens = emptyList(),
                    isTranslating = false
                )
            }
        } else {
            scheduleTranslation(immediate = false)
        }
    }

    fun setStarterPhrase(text: String) {
        onInputChanged(text)
    }

    fun clearInput() {
        phraseJob?.cancel()
        wordJob?.cancel()
        draft.update {
            it.copy(
                inputText = "",
                translatedText = "",
                sourceTokens = emptyList(),
                translatedTokens = emptyList(),
                selectedWord = null,
                isTranslating = false,
                translationError = null
            )
        }
    }

    fun selectSourceLanguage(code: String) {
        if (code == draft.value.sourceCode) return
        if (code == draft.value.targetCode) {
            swapLanguages()
            return
        }
        prefs.sourceLangCode = code
        draft.update {
            it.copy(
                sourceCode = code,
                recentLangCodes = prefs.getRecentLanguageCodes(),
                selectedWord = null,
                translationError = null
            )
        }
        if (draft.value.inputText.isNotBlank()) {
            scheduleTranslation(immediate = true)
        }
    }

    fun selectTargetLanguage(code: String) {
        if (code == draft.value.targetCode) return
        if (code == draft.value.sourceCode) {
            swapLanguages()
            return
        }
        prefs.targetLangCode = code
        draft.update {
            it.copy(
                targetCode = code,
                recentLangCodes = prefs.getRecentLanguageCodes(),
                selectedWord = null,
                translationError = null
            )
        }
        if (draft.value.inputText.isNotBlank()) {
            scheduleTranslation(immediate = true)
        }
    }

    fun swapLanguages() {
        val cur = draft.value
        val newSource = cur.targetCode
        val newTarget = cur.sourceCode
        prefs.sourceLangCode = newSource
        prefs.targetLangCode = newTarget

        val nextInput = if (cur.translatedText.isNotBlank()) cur.translatedText else cur.inputText
        val nextTokens = WordSegmenter.tokenize(nextInput)

        draft.update {
            it.copy(
                sourceCode = newSource,
                targetCode = newTarget,
                inputText = nextInput,
                sourceTokens = nextTokens,
                translatedText = "",
                translatedTokens = emptyList(),
                selectedWord = null,
                translationError = null,
                recentLangCodes = prefs.getRecentLanguageCodes()
            )
        }
        if (nextInput.isNotBlank()) {
            scheduleTranslation(immediate = true)
        }
    }

    fun onWordTapped(token: WordSegmenter.TextToken.Word, origin: WordOrigin) {
        if (repo.modelState.value !is ModelState.Ready) return

        val cur = draft.value
        if (cur.selectedWord?.wordIndex == token.index && cur.selectedWord.origin == origin) {
            dismissWordGloss()
            return
        }

        val (fromCode, toCode) = when (origin) {
            WordOrigin.SOURCE -> cur.sourceCode to cur.targetCode
            WordOrigin.TRANSLATION -> cur.targetCode to cur.sourceCode
        }

        wordJob?.cancel()
        draft.update {
            it.copy(
                selectedWord = SelectedWordGloss(
                    wordIndex = token.index,
                    origin = origin,
                    word = token.lookup,
                    fromLangCode = fromCode,
                    toLangCode = toCode,
                    isLoading = true
                )
            )
        }

        wordJob = viewModelScope.launch {
            try {
                val meaning = repo.translateWord(token.lookup, fromCode, toCode)
                draft.update { state ->
                    val sel = state.selectedWord
                    if (sel != null && sel.wordIndex == token.index && sel.origin == origin) {
                        state.copy(selectedWord = sel.copy(meaning = meaning, isLoading = false))
                    } else {
                        state
                    }
                }
            } catch (t: Throwable) {
                draft.update { state ->
                    val sel = state.selectedWord
                    if (sel != null && sel.wordIndex == token.index && sel.origin == origin) {
                        state.copy(
                            selectedWord = sel.copy(
                                isLoading = false,
                                error = t.localizedMessage ?: "Lookup failed"
                            )
                        )
                    } else {
                        state
                    }
                }
            }
        }
    }

    fun dismissWordGloss() {
        wordJob?.cancel()
        draft.update { it.copy(selectedWord = null) }
    }

    fun toggleTheme() {
        val next = when (draft.value.themeMode) {
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.SYSTEM -> ThemeMode.DARK
        }
        prefs.themeMode = next
        draft.update { it.copy(themeMode = next) }
    }

    fun saveCurrentToHistory() {
        val cur = draft.value
        if (cur.inputText.isBlank() || cur.translatedText.isBlank()) return
        val updated = prefs.addHistoryItem(
            HistoryItem(
                sourceText = cur.inputText.trim(),
                translatedText = cur.translatedText.trim(),
                sourceCode = cur.sourceCode,
                targetCode = cur.targetCode,
                timestampMillis = System.currentTimeMillis()
            )
        )
        draft.update { it.copy(history = updated) }
    }

    fun restoreHistoryItem(item: HistoryItem) {
        prefs.sourceLangCode = item.sourceCode
        prefs.targetLangCode = item.targetCode
        draft.update {
            it.copy(
                sourceCode = item.sourceCode,
                targetCode = item.targetCode,
                inputText = item.sourceText,
                translatedText = item.translatedText,
                sourceTokens = WordSegmenter.tokenize(item.sourceText),
                translatedTokens = WordSegmenter.tokenize(item.translatedText),
                selectedWord = null,
                translationError = null,
                recentLangCodes = prefs.getRecentLanguageCodes()
            )
        }
        if (repo.modelState.value is ModelState.Ready) {
            scheduleTranslation(immediate = true, recordHistory = false)
        }
    }

    fun clearHistory() {
        prefs.clearHistory()
        draft.update { it.copy(history = emptyList()) }
    }

    fun checkForUpdatesManual() {
        if (!isOnline()) {
            draft.update { it.copy(updateState = AppUpdateState.Error("Offline — connect to the internet to check GitHub")) }
            return
        }
        checkForUpdatesInternal(silent = false)
    }

    fun dismissUpdateBanner() {
        draft.update { it.copy(updateBannerDismissed = true) }
    }

    fun downloadUpdate(onReadyToInstall: (File) -> Unit) {
        val current = draft.value.updateState
        val release = when (current) {
            is AppUpdateState.Available -> current.release
            is AppUpdateState.ReadyToInstall -> {
                onReadyToInstall(current.apkFile)
                return
            }
            else -> return
        }
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            draft.update {
                it.copy(
                    updateState = AppUpdateState.Downloading(release, 0f, 0L, release.apkSizeBytes),
                    updateBannerDismissed = false
                )
            }
            try {
                val apkFile = AppUpdateManager.downloadUpdateApk(app, release) { frac, downloaded, total ->
                    draft.update {
                        it.copy(updateState = AppUpdateState.Downloading(release, frac, downloaded, total))
                    }
                }
                draft.update { it.copy(updateState = AppUpdateState.ReadyToInstall(release, apkFile)) }
                onReadyToInstall(apkFile)
            } catch (t: Throwable) {
                draft.update {
                    it.copy(updateState = AppUpdateState.Error(t.localizedMessage ?: "Failed to download update"))
                }
            }
        }
    }

    private fun checkForUpdatesInternal(silent: Boolean) {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch {
            if (!silent) {
                draft.update { it.copy(updateState = AppUpdateState.Checking) }
            }
            try {
                val result = AppUpdateManager.checkForUpdates(BuildConfig.VERSION_NAME)
                prefs.lastUpdateCheckMillis = System.currentTimeMillis()
                prefs.cachedTotalGithubDownloads = result.totalGithubDownloads
                prefs.cachedLatestTag = result.latestRelease?.tagName
                prefs.cachedLatestDownloads = result.latestReleaseDownloads

                val nextState = if (result.updateAvailable && result.latestRelease != null) {
                    AppUpdateState.Available(result.latestRelease)
                } else {
                    AppUpdateState.UpToDate
                }
                draft.update {
                    it.copy(
                        updateState = nextState,
                        updateBannerDismissed = if (nextState is AppUpdateState.Available) false else it.updateBannerDismissed,
                        totalGithubDownloads = result.totalGithubDownloads,
                        latestReleaseDownloads = result.latestReleaseDownloads
                    )
                }
            } catch (t: Throwable) {
                if (!silent) {
                    draft.update {
                        it.copy(updateState = AppUpdateState.Error(t.localizedMessage ?: "Update check failed"))
                    }
                }
            }
        }
    }

    private fun isOnline(): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun syncFromPreferences() {
        val prefSrc = prefs.sourceLangCode
        val prefTgt = prefs.targetLangCode
        val prefHistory = prefs.getHistory()
        val prefRecent = prefs.getRecentLanguageCodes()
        val cur = draft.value
        val pairChanged = cur.sourceCode != prefSrc || cur.targetCode != prefTgt
        draft.update {
            it.copy(
                sourceCode = prefSrc,
                targetCode = prefTgt,
                history = prefHistory,
                recentLangCodes = prefRecent
            )
        }
        if (pairChanged && cur.inputText.isNotBlank()) {
            scheduleTranslation(immediate = true)
        }
    }

    private fun scheduleTranslation(immediate: Boolean, recordHistory: Boolean = true) {
        phraseJob?.cancel()
        if (repo.modelState.value !is ModelState.Ready) return

        val snapshot = draft.value
        val text = snapshot.inputText
        if (text.isBlank()) return

        phraseJob = viewModelScope.launch {
            if (!immediate) delay(380L)
            draft.update { it.copy(isTranslating = true, translationError = null) }
            try {
                val translated = repo.translatePhrase(text, snapshot.sourceCode, snapshot.targetCode)
                val outTokens = WordSegmenter.tokenize(translated)
                draft.update { current ->
                    if (current.inputText == text &&
                        current.sourceCode == snapshot.sourceCode &&
                        current.targetCode == snapshot.targetCode
                    ) {
                        val nextHistory = if (recordHistory && text.trim().length >= 3 && translated.isNotBlank()) {
                            prefs.addHistoryItem(
                                HistoryItem(
                                    sourceText = text.trim(),
                                    translatedText = translated.trim(),
                                    sourceCode = snapshot.sourceCode,
                                    targetCode = snapshot.targetCode,
                                    timestampMillis = System.currentTimeMillis()
                                )
                            )
                        } else {
                            current.history
                        }
                        current.copy(
                            translatedText = translated,
                            translatedTokens = outTokens,
                            isTranslating = false,
                            history = nextHistory
                        )
                    } else {
                        current
                    }
                }
            } catch (t: Throwable) {
                draft.update {
                    it.copy(
                        isTranslating = false,
                        translationError = t.localizedMessage ?: "Translation failed"
                    )
                }
            }
        }
    }
}
