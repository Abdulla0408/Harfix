package uz.harfix.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsTest {
    @Test
    fun versions() {
        assertTrue(News.isNewer("1.4.1", "1.4.0"))
        assertTrue(News.isNewer("1.10", "1.9.9"))
        assertTrue(News.isNewer("2", "1.9"))
        assertFalse(News.isNewer("1.4.0", "1.4.0"))
        assertFalse(News.isNewer("1.4", "1.4.0"))
        assertFalse(News.isNewer("1.3.9", "1.4.0"))
        assertFalse(News.isNewer("", "1.4.0"))
    }

    @Test
    fun safeUrls() {
        assertTrue(Online.isSafeUrl("https://harfix.uz/yuklab-olish/"))
        assertFalse(Online.isSafeUrl("http://harfix.uz"))
        assertFalse(Online.isSafeUrl("https://a.uz/\"><script>"))
        assertFalse(Online.isSafeUrl("javascript:alert(1)"))
        assertFalse(Online.isSafeUrl(null))
    }
}
