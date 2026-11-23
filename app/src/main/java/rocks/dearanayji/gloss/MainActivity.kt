package rocks.dearanayji.gloss

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rocks.dearanayji.gloss.data.LanguageCatalog
import rocks.dearanayji.gloss.ui.MainScreen
import rocks.dearanayji.gloss.ui.MainViewModel
import rocks.dearanayji.gloss.ui.theme.GlossTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) { status ->
            ttsReady = (status == TextToSpeech.SUCCESS)
        }

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
                    onSpeakText = ::speak
                )
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
