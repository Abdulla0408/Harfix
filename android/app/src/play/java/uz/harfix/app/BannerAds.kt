package uz.harfix.app

import android.view.View
import android.widget.FrameLayout

/**
 * The Google Play build has no ad SDK and tells Play Console "no ads", so it never shows a
 * banner or a sponsor card, whatever the online config says (the page reads [ENABLED] through
 * HarfixAndroid.online). The website APK's BannerAds is in src/direct.
 */
@Suppress("UNUSED_PARAMETER")
class BannerAds(activity: MainActivity, private val container: FrameLayout) {
    var unitId: String? = null
        private set
    var imeVisible = false

    fun show(provider: String, unit: String) {}

    fun reset() {
        container.removeAllViews()
        update()
    }

    fun update() {
        container.visibility = View.GONE
    }

    companion object {
        const val ENABLED = false
    }
}
