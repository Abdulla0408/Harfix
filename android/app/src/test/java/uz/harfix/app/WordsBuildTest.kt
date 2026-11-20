package uz.harfix.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Neither build has an accessibility service: the website APK never had one (Google Play Protect
 * blocks those) and the Google Play build dropped it for review (see [Words]).
 */
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
