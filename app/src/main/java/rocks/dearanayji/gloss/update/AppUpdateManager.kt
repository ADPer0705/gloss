package rocks.dearanayji.gloss.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val apkName: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
    val releaseDownloads: Long
)

data class UpdateCheckResult(
    val latestRelease: ReleaseInfo?,
    val updateAvailable: Boolean,
    val totalGithubDownloads: Long,
    val latestReleaseDownloads: Long
)

object SemVer {
    fun parse(version: String): List<Int> {
        val cleaned = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        if (cleaned.isEmpty()) return emptyList()
        return cleaned.split('.').map { it.toIntOrNull() ?: 0 }
    }

    fun isNewer(remoteTag: String, currentVersion: String): Boolean {
        val remote = parse(remoteTag)
        val current = parse(currentVersion)
        if (remote.isEmpty() || current.isEmpty()) return false
        val maxLen = maxOf(remote.size, current.size)
        for (i in 0 until maxLen) {
            val r = remote.getOrElse(i) { 0 }
            val c = current.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}

/**
 * Checks GitHub Releases for newer signed APKs, sums APK download counts
 * across releases, downloads update APKs, and prepares FileProvider install intents.
 */
object AppUpdateManager {

    const val GITHUB_OWNER = "ADPer0705"
    const val GITHUB_REPO = "gloss"
    const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases?per_page=30"
    const val RELEASES_PAGE_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"

    suspend fun checkForUpdates(currentVersion: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        val conn = (URL(RELEASES_API_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Gloss-Android/$currentVersion")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IOException("GitHub API returned HTTP $code")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseReleasesJson(body, currentVersion)
        } finally {
            conn.disconnect()
        }
    }

    fun parseReleasesJson(json: String, currentVersion: String): UpdateCheckResult {
        val arr = JsonParser.parseString(json).asJsonArray
        var totalDownloads = 0L
        var latestRelease: ReleaseInfo? = null

        for (elem in arr) {
            if (!elem.isJsonObject) continue
            val obj = elem.asJsonObject
            if (obj.get("draft")?.asBoolean == true) continue
            val prerelease = obj.get("prerelease")?.asBoolean == true
            val assets = obj.getAsJsonArray("assets") ?: continue

            var releaseApkDownloads = 0L
            var chosenApkName: String? = null
            var chosenApkUrl: String? = null
            var chosenApkSize = 0L

            for (assetElem in assets) {
                if (!assetElem.isJsonObject) continue
                val asset = assetElem.asJsonObject
                val name = asset.get("name")?.asString ?: continue
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                val count = asset.get("download_count")?.asLong ?: 0L
                releaseApkDownloads += count
                if (chosenApkUrl == null) {
                    chosenApkName = name
                    chosenApkUrl = asset.get("browser_download_url")?.asString
                    chosenApkSize = asset.get("size")?.asLong ?: 0L
                }
            }

            totalDownloads += releaseApkDownloads

            if (!prerelease && latestRelease == null && chosenApkUrl != null && chosenApkName != null) {
                val tag = obj.get("tag_name")?.asString.orEmpty()
                latestRelease = ReleaseInfo(
                    tagName = tag,
                    name = obj.get("name")?.asString?.takeIf { it.isNotBlank() } ?: tag,
                    body = obj.get("body")?.asString.orEmpty(),
                    htmlUrl = obj.get("html_url")?.asString ?: RELEASES_PAGE_URL,
                    apkName = chosenApkName,
                    apkDownloadUrl = chosenApkUrl,
                    apkSizeBytes = chosenApkSize,
                    releaseDownloads = releaseApkDownloads
                )
            }
        }

        val isUpdateAvailable = latestRelease?.let { SemVer.isNewer(it.tagName, currentVersion) } == true
        return UpdateCheckResult(
            latestRelease = latestRelease,
            updateAvailable = isUpdateAvailable,
            totalGithubDownloads = totalDownloads,
            latestReleaseDownloads = latestRelease?.releaseDownloads ?: 0L
        )
    }

    suspend fun downloadUpdateApk(
        context: Context,
        release: ReleaseInfo,
        onProgress: (fraction: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Clean stale APKs from previous updates
        updatesDir.listFiles()?.forEach { it.delete() }

        val safeTag = release.tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val partFile = File(updatesDir, "gloss_${safeTag}.apk.part")
        val finalFile = File(updatesDir, "gloss_${safeTag}.apk")

        val conn = openRedirectedConnection(release.apkDownloadUrl)
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IOException("Failed to download update APK (HTTP $code)")
            }
            val expectedTotal = conn.contentLengthLong.takeIf { it > 0L } ?: release.apkSizeBytes
            var downloaded = 0L
            var lastEmit = 0L

            conn.inputStream.use { input ->
                FileOutputStream(partFile, false).use { output ->
                    val buf = ByteArray(65_536)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buf)
                        if (read == -1) break
                        output.write(buf, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 150L) {
                            lastEmit = now
                            val frac = if (expectedTotal > 0L) {
                                (downloaded.toFloat() / expectedTotal.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            onProgress(frac, downloaded, expectedTotal)
                        }
                    }
                    output.flush()
                }
            }

            if (finalFile.exists()) finalFile.delete()
            if (!partFile.renameTo(finalFile)) {
                throw IOException("Failed to finalize update APK")
            }
            onProgress(1f, finalFile.length(), expectedTotal.coerceAtLeast(finalFile.length()))
            finalFile
        } finally {
            conn.disconnect()
        }
    }

    fun createInstallIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun openRedirectedConnection(startUrl: String): HttpURLConnection {
        var currentUrl = startUrl
        for (hop in 0 until 10) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "Gloss-Android")
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
        throw IOException("Too many redirects while downloading update APK")
    }
}
