package rocks.dearanayji.gloss

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rocks.dearanayji.gloss.data.HistoryItem
import rocks.dearanayji.gloss.data.LanguageCatalog
import rocks.dearanayji.gloss.ui.MainScreen
import rocks.dearanayji.gloss.ui.MainViewModel
import rocks.dearanayji.gloss.ui.theme.GlossTheme
import rocks.dearanayji.gloss.update.AppUpdateManager
import rocks.dearanayji.gloss.widget.GlossWidgetActions
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var openHistoryNonce by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) { status ->
            ttsReady = (status == TextToSpeech.SUCCESS)
        }

        handleWidgetIntent(intent)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GlossTheme(themeMode = state.themeMode) {
                MainScreen(
                    state = state,
                    onInputChanged = viewModel::onInputChanged,
                    onStarterSelected = viewModel::setStarterPhrase,
                    onClearInput = viewModel::clearInput,
                    onSourceSelected = viewModel::selectSourceLanguage,
                    onTargetSelected = viewModel::selectTargetLanguage,
                    onSwapLanguages = viewModel::swapLanguages,
                    onWordTapped = viewModel::onWordTapped,
                    onDismissWordGloss = viewModel::dismissWordGloss,
                    onStartDownload = viewModel::startDownload,
                    onCancelDownload = viewModel::cancelDownload,
                    onRetryLoad = viewModel::retryLoad,
                    onDeleteModel = viewModel::deleteModel,
                    onToggleTheme = viewModel::toggleTheme,
                    onSelectHistoryItem = viewModel::restoreHistoryItem,
                    onClearHistory = viewModel::clearHistory,
                    onSaveToHistory = viewModel::saveCurrentToHistory,
                    onSpeakText = ::speak,
                    onCheckForUpdates = viewModel::checkForUpdatesManual,
                    onDownloadUpdate = { viewModel.downloadUpdate(::installApk) },
                    onInstallReadyApk = ::installApk,
                    onDismissUpdateBanner = viewModel::dismissUpdateBanner,
                    openHistoryNonce = openHistoryNonce
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncFromPreferences()
    }

    private fun installApk(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(
                this,
                "Allow Gloss to install app updates, then tap Install again",
                Toast.LENGTH_LONG
            ).show()
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            )
            startActivity(settingsIntent)
            return
        }
        startActivity(AppUpdateManager.createInstallIntent(this, apkFile))
    }

    private fun handleWidgetIntent(intent: Intent?) {
        val action = intent?.getStringExtra(GlossWidgetActions.EXTRA_WIDGET_ACTION) ?: return
        intent.removeExtra(GlossWidgetActions.EXTRA_WIDGET_ACTION)

        when (action) {
            GlossWidgetActions.WIDGET_ACTION_PASTE -> {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipText = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
                if (!clipText.isNullOrBlank()) {
                    viewModel.onInputChanged(clipText)
                } else {
                    Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            }
            GlossWidgetActions.WIDGET_ACTION_HISTORY -> {
                openHistoryNonce++
            }
            GlossWidgetActions.WIDGET_ACTION_RESTORE -> {
                val srcText = intent.getStringExtra(GlossWidgetActions.EXTRA_RESTORE_SOURCE_TEXT)
                val tgtText = intent.getStringExtra(GlossWidgetActions.EXTRA_RESTORE_TRANSLATED_TEXT)
                val srcCode = intent.getStringExtra(GlossWidgetActions.EXTRA_RESTORE_SOURCE_CODE)
                val tgtCode = intent.getStringExtra(GlossWidgetActions.EXTRA_RESTORE_TARGET_CODE)
                if (!srcText.isNullOrBlank() && tgtText != null && !srcCode.isNullOrBlank() && !tgtCode.isNullOrBlank()) {
                    viewModel.restoreHistoryItem(
                        HistoryItem(
                            sourceText = srcText,
                            translatedText = tgtText,
                            sourceCode = srcCode,
                            targetCode = tgtCode,
                            timestampMillis = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    private fun speak(text: String, langCode: String) {
        if (text.isBlank()) return
        val engine = tts
        if (!ttsReady || engine == null) {
            Toast.makeText(this, "Speech engine is initializing…", Toast.LENGTH_SHORT).show()
            return
        }
        val bcp47 = LanguageCatalog.byCode(langCode).bcp47
        if (bcp47 != null) {
            val locale = Locale.forLanguageTag(bcp47)
            val availability = engine.setLanguage(locale)
            if (availability == TextToSpeech.LANG_MISSING_DATA ||
                availability == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Toast.makeText(
                    this,
                    "Offline voice for ${LanguageCatalog.byCode(langCode).name} not installed on device",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gloss_${System.nanoTime()}")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
