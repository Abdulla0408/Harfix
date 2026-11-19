package uz.harfix.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The website APK has no accessibility service at all (Google Play Protect blocks those). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WordsBuildTest {
    @Test
    fun noServiceDeclared() {
        val ctx = RuntimeEnvironment.getApplication()
        assertFalse(Words.available(ctx))
        assertThrows(ClassNotFoundException::class.java) { Class.forName("uz.harfix.app.WordsService") }
    }
}
