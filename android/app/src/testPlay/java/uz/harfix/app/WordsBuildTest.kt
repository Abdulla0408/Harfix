package uz.harfix.app

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The Google Play build declares "Odatiy klaviaturada" (service + tile). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WordsBuildTest {
    @Test
    fun serviceAndTileAreDeclared() {
        val ctx = RuntimeEnvironment.getApplication()
        assertTrue(Words.available(ctx))
        val pm = ctx.packageManager
        val service = pm.getServiceInfo(Words.component(ctx), 0)
        assertTrue(service.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE")
        val tile = pm.getServiceInfo(android.content.ComponentName(ctx.packageName, "uz.harfix.app.WordsTile"), 0)
        assertTrue(tile.permission == "android.permission.BIND_QUICK_SETTINGS_TILE")
    }
}
