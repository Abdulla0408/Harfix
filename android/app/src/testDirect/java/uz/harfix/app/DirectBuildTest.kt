package uz.harfix.app

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uz.harfix.app.keyboard.ClipImages

/** The website APK keeps "Skrinshotlar buferda" (photos permission) and the Yandex banner. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DirectBuildTest {
    @Test
    fun hasScreenshotsAndAds() {
        val ctx = RuntimeEnvironment.getApplication()
        assertTrue(ClipImages.shotsSupported(ctx))
        assertTrue(BannerAds.ENABLED)
        Class.forName("com.yandex.mobile.ads.common.YandexAds")
    }
}
