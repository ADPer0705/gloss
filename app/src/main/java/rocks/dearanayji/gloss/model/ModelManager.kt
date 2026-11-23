package rocks.dearanayji.gloss.model

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val currentFileName: String,
    val currentFileIndex: Int,
    val totalFiles: Int,
    val bytesPerSecond: Long,
    val verifying: Boolean = false
) {
    val fraction: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Manages downloading, resuming, SHA-256 verification, and disk storage of the
 * pinned NLLB-200-distilled-600M INT8 mobile ONNX model.
 */
class ModelManager(private val context: Context) {

    val modelDir: File
        get() = File(context.filesDir, "models/nllb-200-distilled-600M-mobile")

    private val verifiedMarker: File
        get() = File(modelDir, ".verified_${ModelCatalog.PINNED_REVISION.take(12)}")

    fun isModelReady(): Boolean {
        if (!verifiedMarker.isFile) return false
        return ModelCatalog.FILES.all { spec ->
            val file = File(modelDir, spec.name)
            file.isFile && file.length() == spec.sizeBytes
        }
    }

    fun installedSizeBytes(): Long {
        if (!modelDir.exists()) return 0L
        return modelDir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
    }

    fun availableFreeBytes(): Long {
        val target = if (modelDir.exists()) modelDir else context.filesDir
        val stat = StatFs(target.absolutePath)
        return stat.availableBytes
    }

    fun deleteModel(): Boolean {
        if (!modelDir.exists()) return true
        return modelDir.deleteRecursively()
    }

    suspend fun downloadModel(onProgress: (DownloadProgress) -> Unit) = withContext(Dispatchers.IO) {
        if (!modelDir.exists() && !modelDir.mkdirs()) {
            throw IOException("Unable to create model directory: ${modelDir.absolutePath}")
        }

        val requiredFree = ModelCatalog.TOTAL_BYTES - installedSizeBytes() + 100L * 1024L * 1024L
        if (availableFreeBytes() < requiredFree.coerceAtLeast(150L * 1024L * 1024L)) {
            val needMb = (requiredFree.coerceAtLeast(150L * 1024L * 1024L) / (1024 * 1024))
            throw IOException("Not enough free storage. Please free at least $needMb MB and try again.")
        }

        var completedPrefixBytes = 0L
        for (spec in ModelCatalog.FILES) {
            val f = File(modelDir, spec.name)
            if (f.isFile && f.length() == spec.sizeBytes) {
                completedPrefixBytes += spec.sizeBytes
            }
        }

        var totalBytesSoFar = completedPrefixBytes
        for ((index, spec) in ModelCatalog.FILES.withIndex()) {
            coroutineContext.ensureActive()
            val finalFile = File(modelDir, spec.name)
            if (finalFile.isFile && finalFile.length() == spec.sizeBytes) {
                continue
            }

            val partFile = File(modelDir, "${spec.name}.part")
            if (partFile.exists() && partFile.length() > spec.sizeBytes) {
                partFile.delete()
            }

            val existingPart = if (partFile.exists()) partFile.length() else 0L
            if (existingPart > 0L) {
                totalBytesSoFar += existingPart
            }

            if (existingPart < spec.sizeBytes) {
                val url = "${ModelCatalog.RESOLVE_BASE_URL}/${spec.name}"
                totalBytesSoFar = downloadFileWithResume(
                    urlString = url,
                    partFile = partFile,
                    spec = spec,
                    fileIndex = index + 1,
                    initialTotalDownloaded = totalBytesSoFar,
                    onProgress = onProgress
                )
            }

            onProgress(
                DownloadProgress(
                    downloadedBytes = totalBytesSoFar,
                    totalBytes = ModelCatalog.TOTAL_BYTES,
                    currentFileName = spec.name,
                    currentFileIndex = index + 1,
                    totalFiles = ModelCatalog.FILES.size,
                    bytesPerSecond = 0L,
                    verifying = true
                )
            )

            val actualSha = sha256(partFile)
            if (!actualSha.equals(spec.sha256, ignoreCase = true)) {
                partFile.delete()
                throw IOException("Checksum mismatch for ${spec.name}. Please retry the download.")
            }

            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                throw IOException("Failed to finalize ${spec.name}")
            }
        }

        // Remove stale markers and write current verified marker
        modelDir.listFiles()?.filter { it.name.startsWith(".verified") }?.forEach { it.delete() }
        verifiedMarker.writeText("${ModelCatalog.REPO_ID}@${ModelCatalog.PINNED_REVISION}\n")
    }

    private suspend fun downloadFileWithResume(
        urlString: String,
        partFile: File,
        spec: ModelFileSpec,
        fileIndex: Int,
        initialTotalDownloaded: Long,
        onProgress: (DownloadProgress) -> Unit
    ): Long {
        var resumeFrom = if (partFile.exists()) partFile.length() else 0L
        var baseTotal = initialTotalDownloaded - resumeFrom

        val conn = openRedirectedConnection(urlString, resumeFrom)
        try {
            val code = conn.responseCode
            val append = when (code) {
                HttpURLConnection.HTTP_PARTIAL -> true
                HttpURLConnection.HTTP_OK -> {
                    resumeFrom = 0L
                    false
                }
                416 -> {
                    // Range not satisfiable; restart cleanly
                    partFile.delete()
                    conn.disconnect()
                    return downloadFileWithResume(
                        urlString = urlString,
                        partFile = partFile,
                        spec = spec,
                        fileIndex = fileIndex,
                        initialTotalDownloaded = baseTotal,
                        onProgress = onProgress
                    )
                }
                else -> throw IOException("Download failed for ${spec.name} (HTTP $code)")
            }

            var currentTotal = baseTotal + resumeFrom
            var lastEmitTime = System.currentTimeMillis()
            var windowBytes = 0L
            var currentSpeed = 0L

            conn.inputStream.use { input ->
                FileOutputStream(partFile, append).use { output ->
                    val buffer = ByteArray(65_536)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        currentTotal += read
                        windowBytes += read

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastEmitTime
                        if (elapsed >= 200L) {
                            currentSpeed = (windowBytes * 1000L) / elapsed.coerceAtLeast(1L)
                            windowBytes = 0L
                            lastEmitTime = now
                            onProgress(
                                DownloadProgress(
                                    downloadedBytes = currentTotal.coerceAtMost(ModelCatalog.TOTAL_BYTES),
                                    totalBytes = ModelCatalog.TOTAL_BYTES,
                                    currentFileName = spec.name,
                                    currentFileIndex = fileIndex,
                                    totalFiles = ModelCatalog.FILES.size,
                                    bytesPerSecond = currentSpeed,
                                    verifying = false
                                )
                            )
                        }
                    }
                    output.flush()
                }
            }

            if (partFile.length() != spec.sizeBytes) {
                throw IOException("Incomplete download for ${spec.name} (${partFile.length()} / ${spec.sizeBytes} bytes)")
            }

            onProgress(
                DownloadProgress(
                    downloadedBytes = currentTotal.coerceAtMost(ModelCatalog.TOTAL_BYTES),
                    totalBytes = ModelCatalog.TOTAL_BYTES,
                    currentFileName = spec.name,
                    currentFileIndex = fileIndex,
                    totalFiles = ModelCatalog.FILES.size,
                    bytesPerSecond = currentSpeed,
                    verifying = false
                )
            )
            return currentTotal
        } finally {
            conn.disconnect()
        }
    }

    private fun openRedirectedConnection(startUrl: String, rangeStart: Long): HttpURLConnection {
        var currentUrl = startUrl
        for (hop in 0 until 10) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "Gloss-Android/2.0")
                if (rangeStart > 0L) {
                    setRequestProperty("Range", "bytes=$rangeStart-")
                }
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 ||
                code == 308
            ) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IOException("Redirect without Location header from $currentUrl")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
            } else {
                return conn
            }
        }
        throw IOException("Too many redirects while downloading $startUrl")
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(131_072)
        FileInputStream(file).use { fis ->
            while (true) {
                val read = fis.read(buf)
                if (read == -1) break
                md.update(buf, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
