package rocks.dearanayji.gloss.data

import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rocks.dearanayji.gloss.engine.NllbTranslatorEngine
import rocks.dearanayji.gloss.model.DownloadProgress
import rocks.dearanayji.gloss.model.ModelCatalog
import rocks.dearanayji.gloss.model.ModelManager

sealed interface ModelState {
    data object NotDownloaded : ModelState
    data class Downloading(val progress: DownloadProgress) : ModelState
    data object Loading : ModelState
    data object Ready : ModelState
    data class Error(val message: String, val downloadedBeforeFailure: Boolean) : ModelState
}

/**
 * Application-scoped coordinator owning model downloads, ONNX engine lifecycle,
 * serialized inference execution, and a word-translation LRU cache.
 */
class EngineRepository(
    private val modelManager: ModelManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engineMutex = Mutex()
    private var engine: NllbTranslatorEngine? = null
    private var downloadJob: Job? = null

    private val wordCache = LruCache<String, String>(256)

    private val _modelState = MutableStateFlow<ModelState>(
        if (modelManager.isModelReady()) ModelState.Loading else ModelState.NotDownloaded
    )
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    fun refreshInitialState() {
        if (modelManager.isModelReady()) {
            ensureLoadedAsync()
        } else {
            _modelState.value = ModelState.NotDownloaded
        }
    }

    fun startDownload() {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            try {
                _modelState.value = ModelState.Downloading(
                    DownloadProgress(
                        downloadedBytes = modelManager.installedSizeBytes()
                            .coerceAtMost(ModelCatalog.TOTAL_BYTES),
                        totalBytes = ModelCatalog.TOTAL_BYTES,
                        currentFileName = ModelCatalog.FILES.first().name,
                        currentFileIndex = 1,
                        totalFiles = ModelCatalog.FILES.size,
                        bytesPerSecond = 0L
                    )
                )
                modelManager.downloadModel { progress ->
                    _modelState.value = ModelState.Downloading(progress)
                }
                loadEngineInternal()
            } catch (_: CancellationException) {
                _modelState.value = if (modelManager.isModelReady()) {
                    ModelState.Loading
                } else {
                    ModelState.NotDownloaded
                }
            } catch (t: Throwable) {
                _modelState.value = ModelState.Error(
                    message = t.localizedMessage ?: "Download failed",
                    downloadedBeforeFailure = modelManager.isModelReady()
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    fun ensureLoadedAsync() {
        if (engine != null) {
            _modelState.value = ModelState.Ready
            return
        }
        if (!modelManager.isModelReady()) {
            _modelState.value = ModelState.NotDownloaded
            return
        }
        scope.launch {
            loadEngineInternal()
        }
    }

    private suspend fun loadEngineInternal() {
        engineMutex.withLock {
            if (engine != null) {
                _modelState.value = ModelState.Ready
                return
            }
            if (!modelManager.isModelReady()) {
                _modelState.value = ModelState.NotDownloaded
                return
            }
            _modelState.value = ModelState.Loading
            try {
                val loaded = withContext(Dispatchers.Default) {
                    NllbTranslatorEngine.load(modelManager.modelDir)
                }
                engine = loaded
                _modelState.value = ModelState.Ready
            } catch (t: Throwable) {
                _modelState.value = ModelState.Error(
                    message = "Unable to load model: ${t.localizedMessage ?: "unknown error"}",
                    downloadedBeforeFailure = true
                )
            }
        }
    }

    suspend fun translatePhrase(
        text: String,
        sourceCode: String,
        targetCode: String
    ): String = withContext(Dispatchers.Default) {
        engineMutex.withLock {
            val active = engine ?: throw IllegalStateException("Model not loaded")
            active.translate(text, sourceCode, targetCode)
        }
    }

    suspend fun translateWord(
        word: String,
        sourceCode: String,
        targetCode: String
    ): String {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return ""
        val cacheKey = "$sourceCode->$targetCode:${trimmed.lowercase()}"
        wordCache.get(cacheKey)?.let { return it }

        val translated = withContext(Dispatchers.Default) {
            engineMutex.withLock {
                val active = engine ?: throw IllegalStateException("Model not loaded")
                active.translate(trimmed, sourceCode, targetCode)
            }
        }
        if (translated.isNotBlank()) {
            wordCache.put(cacheKey, translated)
        }
        return translated
    }

    suspend fun deleteModel() {
        downloadJob?.cancel()
        downloadJob = null
        engineMutex.withLock {
            withContext(Dispatchers.Default) {
                engine?.close()
                engine = null
            }
            wordCache.evictAll()
            withContext(Dispatchers.IO) {
                modelManager.deleteModel()
            }
            _modelState.value = ModelState.NotDownloaded
        }
    }

    fun installedSizeBytes(): Long = modelManager.installedSizeBytes()
}
