package uz.harfix.app.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipImagesTest {
    @Test
    fun knowsScreenshots() {
        assertTrue(ClipImages.isScreenshot("Screenshot_2026-11-19-10-39-12-345_org.telegram.messenger.jpg", "DCIM/Screenshots/"))
        assertTrue(ClipImages.isScreenshot("IMG_1.jpg", "Pictures/Screenshots/")) // by folder
        assertTrue(ClipImages.isScreenshot("Screenshot_20261119.png", null)) // by name (old phones)
        assertFalse(ClipImages.isScreenshot("IMG_20261119_103912.jpg", "DCIM/Camera/"))
        assertFalse(ClipImages.isScreenshot(null, null))
    }

    @Test
    fun picturesKeepTheirType() {
        assertEquals("image/png", ClipImages.mime("shot_12.png"))
        assertEquals("image/jpeg", ClipImages.mime("pick_1.jpg"))
        assertEquals("image/gif", ClipImages.mime("pick_2.gif"))
        assertEquals("png", ClipImages.extension("image/png"))
        assertEquals("jpg", ClipImages.extension(null))
    }

    @Test
    fun clipboardPanelKeepsItsHeight() {
        val texts = (1..10).map { "matn $it" }
        val plain = Layouts.clipboard(texts).rows.sumOf { it.height.toDouble() }
        for (layout in listOf(Layouts.clipboard(texts, 3, true), Layouts.clipboard(texts, 1, false), Layouts.clipboard(emptyList(), 0, true))) {
            assertEquals(plain, layout.rows.sumOf { it.height.toDouble() }, 0.01)
        }
        val keys = Layouts.clipboard(texts, 2, false).rows.flatMap { it.keys }.map { it.action }
        assertTrue(keys.containsAll(listOf("img:0", "img:1", "image:pick", "clip:clear")))
        assertFalse(keys.contains("img:2"))
    }
}
