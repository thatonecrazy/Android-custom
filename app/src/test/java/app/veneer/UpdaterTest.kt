package app.veneer

import app.veneer.system.ReleaseInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {

    /** The shape of GitHub's "latest release" answer, trimmed to the fields that matter. */
    private val latest = """
        {"tag_name":"v1.9","name":"Veneer 1.9","body":"Update Veneer from inside the app\n\nMore details.\n",
         "html_url":"https://github.com/thatonecrazy/Android-custom/releases/tag/v1.9",
         "assets":[
           {"name":"notes.txt","size":10,"browser_download_url":"https://example.invalid/notes.txt"},
           {"name":"Veneer-1.9.apk","size":3786725,"content_type":"application/vnd.android.package-archive",
            "browser_download_url":"https://github.com/thatonecrazy/Android-custom/releases/download/v1.9/Veneer-1.9.apk"}
         ]}
    """.trimIndent()

    @Test
    fun readsTheLatestRelease() {
        val release = ReleaseInfo.parse(latest)!!
        assertEquals("1.9", release.version)
        assertEquals("https://github.com/thatonecrazy/Android-custom/releases/download/v1.9/Veneer-1.9.apk", release.apkUrl)
        assertEquals(3786725L, release.sizeBytes)
        assertEquals("Update Veneer from inside the app", release.notes.lines().first())
    }

    @Test
    fun aReleaseWithoutAnApkIsIgnored() {
        assertNull(ReleaseInfo.parse("""{"tag_name":"v2.0","assets":[]}"""))
    }

    @Test
    fun comparesVersionsNumberByNumber() {
        assertTrue(ReleaseInfo.isNewer("1.9", "1.8"))
        assertTrue(ReleaseInfo.isNewer("1.10", "1.9"))
        assertTrue(ReleaseInfo.isNewer("1.8.1", "1.8"))
        assertTrue(ReleaseInfo.isNewer("v2.0", "1.9"))
        assertFalse(ReleaseInfo.isNewer("1.8", "1.8"))
        assertFalse(ReleaseInfo.isNewer("1.8", "1.8.0"))
        assertFalse(ReleaseInfo.isNewer("1.7", "1.8"))
    }
}
