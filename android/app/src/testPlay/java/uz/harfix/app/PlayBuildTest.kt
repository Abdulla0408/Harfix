package uz.harfix.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uz.harfix.app.keyboard.ClipImages

/**
 * The Google Play build stays plain for review (android/PLAY.md, section 0): no photos
 * permission, no ad SDK, no ads shown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayBuildTest {
    @Test
    fun noPhotosPermission() {
        val ctx = RuntimeEnvironment.getApplication()
        assertFalse(ClipImages.shotsSupported(ctx))
        @Suppress("DEPRECATION")
        val asked = ctx.packageManager.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toList()
        assertFalse(asked.toString(), asked.any { it.contains("MEDIA_IMAGES") || it.contains("READ_EXTERNAL_STORAGE") })
        assertFalse(asked.toString(), asked.any { it.endsWith("AD_ID") })
    }

    /** The page gets BannerAds.ENABLED as "ads" (Bridge.online) and then shows no banner or sponsor card. */
    @Test
    fun noAds() {
        assertFalse(BannerAds.ENABLED)
        assertThrows(ClassNotFoundException::class.java) { Class.forName("com.yandex.mobile.ads.common.YandexAds") }
    }
}
