package rocks.dearanayji.gloss.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rocks.dearanayji.gloss.data.LanguageCatalog
import rocks.dearanayji.gloss.data.ModelState
import rocks.dearanayji.gloss.ui.components.DownloadCard
import rocks.dearanayji.gloss.ui.components.HistorySheet
import rocks.dearanayji.gloss.ui.components.InteractiveWordText
import rocks.dearanayji.gloss.ui.components.LanguageBar
import rocks.dearanayji.gloss.ui.components.LanguagePickerSheet
import rocks.dearanayji.gloss.ui.components.ModelManagerSheet
import rocks.dearanayji.gloss.ui.components.WordGlossCard
import rocks.dearanayji.gloss.ui.theme.LocalGlossColors
import java.io.File

private enum class PickerTarget { SOURCE, TARGET }

private val STARTER_PHRASES = mapOf(
    "eng_Latn" to listOf(
        "Where is the train station?",
        "Could you speak a little slower, please?",
        "I am learning a new language every day."
    ),
    "spa_Latn" to listOf(
        "¿Dónde está la biblioteca?",
        "Me gustaría un café con leche, por favor.",
        "Estoy aprendiendo un nuevo idioma."
    ),
    "fra_Latn" to listOf(
        "Où se trouve la gare, s'il vous plaît ?",
        "J'apprends une nouvelle langue."
    ),
    "deu_Latn" to listOf(
        "Wo ist der Bahnhof, bitte?",
        "Ich lerne jeden Tag neue Wörter."
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    state: GlossUiState,
    onInputChanged: (String) -> Unit,
    onStarterSelected: (String) -> Unit,
    onClearInput: () -> Unit,
    onSourceSelected: (String) -> Unit,
    onTargetSelected: (String) -> Unit,
    onSwapLanguages: () -> Unit,
    onWordTapped: (rocks.dearanayji.gloss.engine.WordSegmenter.TextToken.Word, WordOrigin) -> Unit,
    onDismissWordGloss: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRetryLoad: () -> Unit,
    onDeleteModel: () -> Unit,
    onToggleTheme: () -> Unit,
    onSelectHistoryItem: (rocks.dearanayji.gloss.data.HistoryItem) -> Unit,
    onClearHistory: () -> Unit,
    onSaveToHistory: () -> Unit,
    onSpeakText: (String, String) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallReadyApk: (File) -> Unit,
    onDismissUpdateBanner: () -> Unit,
    openHistoryNonce: Int = 0
) {
    val colors = LocalGlossColors.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }

    LaunchedEffect(openHistoryNonce) {
        if (openHistoryNonce > 0) {
            showHistorySheet = true
        }
    }

    val sourceLang = remember(state.sourceCode) { LanguageCatalog.byCode(state.sourceCode) }
    val targetLang = remember(state.targetCode) { LanguageCatalog.byCode(state.targetCode) }
    val isEngineReady = state.modelState is ModelState.Ready ||
        state.modelState is ModelState.IdleDownloaded ||
        state.modelState is ModelState.Loading

    Scaffold(
        containerColor = colors.bg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Top Bar ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "gloss",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = { showHistorySheet = true }) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = "Phrase history",
                        tint = colors.textSecondary
                    )
                }

                IconButton(onClick = onToggleTheme) {
                    Icon(
                        imageVector = if (colors.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = "Toggle theme",
                        tint = colors.textSecondary
                    )
                }

                StatusPill(
                    modelState = state.modelState,
                    onClick = { showModelSheet = true }
                )
            }

            // ── Language Selector Row ──
            LanguageBar(
                sourceLanguage = sourceLang,
                targetLanguage = targetLang,
                onSourceClick = { pickerTarget = PickerTarget.SOURCE },
                onTargetClick = { pickerTarget = PickerTarget.TARGET },
                onSwapClick = onSwapLanguages
            )

            // ── Optional App Update Banner ──
            if (!state.updateBannerDismissed &&
                (state.updateState is AppUpdateState.Available ||
                    state.updateState is AppUpdateState.Downloading ||
                    state.updateState is AppUpdateState.ReadyToInstall)
            ) {
                UpdateBanner(
                    updateState = state.updateState,
                    onDownloadUpdate = onDownloadUpdate,
                    onInstallReadyApk = onInstallReadyApk,
                    onDismiss = onDismissUpdateBanner
                )
            }

            // ── Offline Download / Loading Card ──
            DownloadCard(
                modelState = state.modelState,
                onStartDownload = onStartDownload,
                onCancelDownload = onCancelDownload,
                onRetryLoad = onRetryLoad
            )

            // ── Source Input Card ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sourceLang.name.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.inputText.isNotEmpty()) {
                            IconButton(
                                onClick = { onSpeakText(state.inputText, state.sourceCode) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = "Speak input",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onClearInput,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear text",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    val clip = clipboard.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        onInputChanged(clip)
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Clipboard is empty")
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (state.inputText.isEmpty()) {
                            Text(
                                text = "Type or paste a sentence to translate…",
                                color = colors.textHint,
                                fontSize = 19.sp,
                                lineHeight = 27.sp
                            )
                        }
                        BasicTextField(
                            value = state.inputText,
                            onValueChange = onInputChanged,
                            textStyle = TextStyle(
                                color = colors.textPrimary,
                                fontSize = 19.sp,
                                lineHeight = 27.sp
                            ),
                            cursorBrush = SolidColor(colors.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (state.inputText.isEmpty()) {
                        val samples = STARTER_PHRASES[state.sourceCode]
                        if (!samples.isNullOrEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "TRY AN EXAMPLE",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textHint,
                                fontSize = 10.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                samples.forEach { sample ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = colors.surfaceElevated,
                                        border = BorderStroke(1.dp, colors.border),
                                        modifier = Modifier.clickable { onStarterSelected(sample) }
                                    ) {
                                        Text(
                                            text = sample,
                                            color = colors.textSecondary,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (state.sourceTokens.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = colors.border)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = if (isEngineReady) "TAP A WORD TO GLOSS" else "DOWNLOAD MODEL TO TAP WORDS",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            fontSize = 10.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        InteractiveWordText(
                            tokens = state.sourceTokens,
                            selectedWordIndex = if (state.selectedWord?.origin == WordOrigin.SOURCE) {
                                state.selectedWord.wordIndex
                            } else {
                                null
                            },
                            enabled = isEngineReady,
                            fontSize = 16.sp,
                            accentColor = colors.primary,
                            onWordTap = { onWordTapped(it, WordOrigin.SOURCE) }
                        )
                    }
                }
            }

            // ── Inline Duolingo-style Word Gloss Card ──
            WordGlossCard(
                gloss = state.selectedWord,
                onSpeakWord = onSpeakText,
                onCopyWord = { text ->
                    clipboard.setText(AnnotatedString(text))
                    scope.launch { snackbarHostState.showSnackbar("Copied \"$text\"") }
                },
                onDismiss = onDismissWordGloss
            )

            // ── Translated Output Card (also tappable word-by-word!) ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = targetLang.name.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.translatedText.isNotBlank()) {
                            IconButton(
                                onClick = { onSpeakText(state.translatedText, state.targetCode) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                    contentDescription = "Speak translation",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(state.translatedText))
                                    onSaveToHistory()
                                    scope.launch { snackbarHostState.showSnackbar("Translation copied") }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy translation",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    if (state.isTranslating) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            color = colors.secondary,
                            trackColor = colors.surfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    when {
                        state.translationError != null -> {
                            Text(
                                text = state.translationError,
                                color = colors.statusError,
                                fontSize = 14.sp
                            )
                        }
                        state.translatedText.isBlank() -> {
                            Text(
                                text = if (isEngineReady) {
                                    "Translation will appear here. Every translated word is also tappable."
                                } else {
                                    "Download the offline model above to start translating."
                                },
                                color = colors.textHint,
                                fontSize = 17.sp,
                                lineHeight = 25.sp
                            )
                        }
                        else -> {
                            InteractiveWordText(
                                tokens = state.translatedTokens,
                                selectedWordIndex = if (state.selectedWord?.origin == WordOrigin.TRANSLATION) {
                                    state.selectedWord.wordIndex
                                } else {
                                    null
                                },
                                enabled = isEngineReady,
                                fontSize = 20.sp,
                                accentColor = colors.secondary,
                                onWordTap = { onWordTapped(it, WordOrigin.TRANSLATION) }
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Tap any translated word to see its meaning in ${sourceLang.name}",
                                color = colors.textHint,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    pickerTarget?.let { target ->
        LanguagePickerSheet(
            title = if (target == PickerTarget.SOURCE) "Translate from" else "Translate to",
            selectedCode = if (target == PickerTarget.SOURCE) state.sourceCode else state.targetCode,
            recentCodes = state.recentLangCodes,
            onSelect = { lang ->
                if (target == PickerTarget.SOURCE) {
                    onSourceSelected(lang.code)
                } else {
                    onTargetSelected(lang.code)
                }
            },
            onDismiss = { pickerTarget = null }
        )
    }

    if (showModelSheet) {
        ModelManagerSheet(
            modelState = state.modelState,
            installedSizeBytes = state.installedSizeBytes,
            lastLoadMetrics = state.lastLoadMetrics,
            currentVersionName = state.currentVersionName,
            updateState = state.updateState,
            totalGithubDownloads = state.totalGithubDownloads,
            latestReleaseDownloads = state.latestReleaseDownloads,
            onCheckForUpdates = onCheckForUpdates,
            onDownloadUpdate = onDownloadUpdate,
            onInstallReadyApk = onInstallReadyApk,
            onStartDownload = onStartDownload,
            onDeleteModel = onDeleteModel,
            onDismiss = { showModelSheet = false }
        )
    }

    if (showHistorySheet) {
        HistorySheet(
            items = state.history,
            onSelectItem = onSelectHistoryItem,
            onClearHistory = onClearHistory,
            onDismiss = { showHistorySheet = false }
        )
    }
}

@Composable
private fun UpdateBanner(
    updateState: AppUpdateState,
    onDownloadUpdate: () -> Unit,
    onInstallReadyApk: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalGlossColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.secondary)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = colors.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    when (updateState) {
                        is AppUpdateState.Available -> {
                            Text(
                                text = "Update ${updateState.release.tagName} available",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Tap Update to install in-place without losing your offline model.",
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        is AppUpdateState.Downloading -> {
                            Text(
                                text = "Downloading ${updateState.release.tagName} (${(updateState.fraction * 100).toInt()}%)",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                        is AppUpdateState.ReadyToInstall -> {
                            Text(
                                text = "${updateState.release.tagName} ready to install",
                                color = colors.statusReady,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                        else -> Unit
                    }
                }
                when (updateState) {
                    is AppUpdateState.Available -> {
                        Button(
                            onClick = onDownloadUpdate,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.secondary)
                        ) {
                            Text("Update", color = colors.bg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                    is AppUpdateState.ReadyToInstall -> {
                        Button(
                            onClick = { onInstallReadyApk(updateState.apkFile) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.statusReady)
                        ) {
                            Text("Install", color = colors.bg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    else -> Unit
                }
            }
            if (updateState is AppUpdateState.Downloading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { updateState.fraction },
                    color = colors.secondary,
                    trackColor = colors.surfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    modelState: ModelState,
    onClick: () -> Unit
) {
    val colors = LocalGlossColors.current
    val (dotColor, label) = when (modelState) {
        ModelState.Ready, ModelState.IdleDownloaded -> colors.statusReady to "Offline"
        ModelState.Loading -> colors.statusWarning to "Loading"
        is ModelState.Downloading -> {
            val pct = (modelState.progress.fraction * 100f).toInt().coerceIn(0, 100)
            colors.statusWarning to "$pct%"
        }
        ModelState.NotDownloaded -> colors.textSecondary to "Setup"
        is ModelState.Error -> colors.statusError to "Error"
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = label,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
