package rocks.dearanayji.gloss.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun `SemVer compares semantic versions accurately`() {
        assertTrue(SemVer.isNewer("v2.3.0", "2.2.0"))
        assertTrue(SemVer.isNewer("v10.0.0", "v2.9.9"))
        assertTrue(SemVer.isNewer("2.3.1", "2.3.0"))
        assertFalse(SemVer.isNewer("v2.3.0", "2.3.0"))
        assertFalse(SemVer.isNewer("v2.2.0", "2.3.0"))
        assertFalse(SemVer.isNewer("invalid", "2.3.0"))
    }

    @Test
    fun `parseReleasesJson selects latest non-draft non-prerelease APK and sums downloads`() {
        val sampleJson = """
            [
              {
                "tag_name": "v2.4.0-beta1",
                "name": "Beta",
                "draft": false,
                "prerelease": true,
                "html_url": "https://github.com/ADPer0705/gloss/releases/tag/v2.4.0-beta1",
                "body": "beta notes",
                "assets": [
                  {
                    "name": "gloss_v2.4.0-beta1.apk",
                    "browser_download_url": "https://example.com/beta.apk",
                    "size": 70000000,
                    "download_count": 3
                  }
                ]
              },
              {
                "tag_name": "v2.4.0",
                "name": "Gloss v2.4.0",
                "draft": false,
                "prerelease": false,
                "html_url": "https://github.com/ADPer0705/gloss/releases/tag/v2.4.0",
                "body": "Release notes",
                "assets": [
                  {
                    "name": "gloss_v2.4.0.apk",
                    "browser_download_url": "https://example.com/gloss_v2.4.0.apk",
                    "size": 74000000,
                    "download_count": 12
                  }
                ]
              },
              {
                "tag_name": "v2.2.0",
                "name": "Gloss v2.2.0",
                "draft": false,
                "prerelease": false,
                "html_url": "https://github.com/ADPer0705/gloss/releases/tag/v2.2.0",
                "body": "Older",
                "assets": [
                  {
                    "name": "gloss_v2.2.0.apk",
                    "browser_download_url": "https://example.com/gloss_v2.2.0.apk",
                    "size": 71000000,
                    "download_count": 25
                  }
                ]
              }
            ]
        """.trimIndent()

        val result = AppUpdateManager.parseReleasesJson(sampleJson, "2.3.0")
        assertTrue(result.updateAvailable)
        assertNotNull(result.latestRelease)
        assertEquals("v2.4.0", result.latestRelease?.tagName)
        assertEquals("https://example.com/gloss_v2.4.0.apk", result.latestRelease?.apkDownloadUrl)
        assertEquals(40L, result.totalGithubDownloads)
        assertEquals(12L, result.latestReleaseDownloads)

        val upToDate = AppUpdateManager.parseReleasesJson(sampleJson, "2.4.0")
        assertFalse(upToDate.updateAvailable)
    }
}
