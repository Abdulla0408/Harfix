package uz.harfix.app

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.YandexAds

/**
 * The banner under the page. The web page decides (from the online config) whether and
 * which ad unit to show, through HarfixAndroid.showBanner / hideBanner.
 */
class BannerAds(private val activity: MainActivity, private val container: FrameLayout) {
    private var view: BannerAdView? = null
    var unitId: String? = null
        private set
    private var loaded = false
    /** Hidden while the on-screen keyboard is open. */
    var imeVisible = false

    fun show(provider: String, unit: String) {
        if (provider != "yandex" || unit.isBlank() || activity.isDestroyed) return
        if (unit == unitId) return
        reset()
        unitId = unit
        if (initialized) {
            load(unit)
        } else {
            YandexAds.initialize(activity.applicationContext) {
                initialized = true
                activity.runOnUiThread { if (unitId == unit && !activity.isDestroyed) load(unit) }
            }
        }
    }

    fun reset() {
        view?.destroy()
        view = null
        unitId = null
        loaded = false
        container.removeAllViews()
        update()
    }

    private fun load(unit: String) {
        val density = activity.resources.displayMetrics.density
        val widthPx = if (container.width > 0) container.width else activity.resources.displayMetrics.widthPixels
        val v = BannerAdView(activity)
        v.setAdSize(BannerAdSize.sticky(activity, (widthPx / density).toInt()))
        v.setBannerAdEventListener(object : BannerAdEventListener {
            override fun onAdLoaded() {
                loaded = true
                update()
            }

            override fun onAdFailedToLoad(error: AdRequestError) {
                loaded = false
                update()
            }

            override fun onAdClicked() {}
            override fun onImpression(impressionData: ImpressionData?) {}
        })
        container.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view = v
        v.loadAd(AdRequest.Builder(unit).build())
    }

    fun update() {
        container.visibility = if (loaded && !imeVisible) View.VISIBLE else View.GONE
    }

    companion object {
        @Volatile private var initialized = false
    }
}
