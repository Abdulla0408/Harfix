package uz.harfix.app

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject

/** window.HarfixAndroid: what the web page (assets/ui) can ask the phone to do. */
class Bridge(private val a: MainActivity) {

    private fun prefs(): SharedPreferences = a.getSharedPreferences("harfix", 0)

    @JavascriptInterface
    fun ready() = a.runOnUiThread { a.onPageReady() }

    @JavascriptInterface
    fun state(): String {
        val p = prefs()
        val imm = a.getSystemService(InputMethodManager::class.java)
        val enabled = imm.enabledInputMethodList.any { it.packageName == a.packageName }
        val current = Settings.Secure.getString(a.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val selected = current != null && current.startsWith("${a.packageName}/")
        val notify = p.getBoolean("notify", true) &&
            a.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        val settings = JSONObject()
            .put("target", Converter.target(p.getString("target", "new")))
            .put("live", p.getBoolean("live", true))
            .put("sound", p.getBoolean("sound", false))
            .put("vibrate", p.getBoolean("vibrate", true))
            .put("lang_ru", p.getBoolean("lang_ru", true))
            .put("lang_uz_cyrl", p.getBoolean("lang_uz_cyrl", true))
            .put("words", p.getBoolean("words", true))
            .put("words_live", p.getBoolean("words_live", true))
            .put("autocorrect", p.getBoolean("autocorrect", true))
            .put("glide", p.getBoolean("glide", true))
            .put("learn", p.getBoolean("learn", true))
            .put("stats", p.getBoolean("stats", true))
            .put("notify", notify)
            .put("quiz_best", p.getInt("quiz_best", 0))
        // Android 13+ blocks accessibility for apps installed outside a store until the user
        // allows "restricted settings"; the page then explains how.
        val restricted = Build.VERSION.SDK_INT >= 33 && !Online.fromPlay(a) && !Words.inSecureSettings(a)
        return JSONObject()
            .put("version", a.packageManager.getPackageInfo(a.packageName, 0).versionName)
            .put("settings", settings)
            .put("keyboard", JSONObject().put("enabled", enabled).put("selected", selected))
            .put("words", Words.enabled(a))
            .put("words_seen", p.getBoolean("words_seen", false))
            .put("words_available", Words.available(a))
            .put("words_ok", p.getBoolean("words_ok", false))
            .put("restricted", restricted)
            .toString()
    }

    @JavascriptInterface
    fun set(key: String, value: String) {
        val e = prefs().edit()
        when (key) {
            "live", "sound", "vibrate", "lang_ru", "lang_uz_cyrl", "words", "words_live", "stats",
            "autocorrect", "glide", "learn" ->
                e.putBoolean(key, value == "true")
            "target" -> e.putString(key, value)
        }
        e.apply()
        if (key == "words") Words.refreshTile(a)
        // the keyboard's learned words live only on the phone; switching learning off deletes them
        if (key == "learn" && value != "true") java.io.File(a.filesDir, "user_words.txt").delete()
        if (key == "stats" && value == "true") {
            Online.nextTouch = 0
            Online.touch(a)
        }
        if (key == "notify") {
            prefs().edit().putBoolean("notify", value == "true").apply()
            if (value == "true") a.runOnUiThread { a.askNotifications() }
        }
    }

    @JavascriptInterface
    fun convert(itemsJson: String, target: String) {
        val items = JSONArray(itemsJson)
        a.worker.execute { a.convertFiles(items, target) }
    }

    @JavascriptInterface
    fun pickFiles() = a.runOnUiThread { a.pickFiles() }

    @JavascriptInterface
    fun open(uri: String, name: String) {
        a.start(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(uri), Files.mime(name))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    @JavascriptInterface
    fun share(uri: String, name: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(Files.mime(name))
            .putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        a.start(Intent.createChooser(send, name))
    }

    @JavascriptInterface
    fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        a.start(Intent.createChooser(send, "Harfix"))
    }

    @JavascriptInterface
    fun copy(text: String) {
        a.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Harfix", text))
    }

    @JavascriptInterface
    fun keyboardSettings() = a.start(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))

    @JavascriptInterface
    fun keyboardPicker() = a.runOnUiThread {
        a.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }

    @JavascriptInterface
    fun appSettings() {
        a.start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.packageName, null)))
    }

    @JavascriptInterface
    fun agreeWords() {
        prefs().edit().putBoolean("words_ok", true).apply()
    }

    @JavascriptInterface
    fun accessibilitySettings() {
        prefs().edit().putBoolean("words_ok", true).apply()
        a.runOnUiThread {
            try {
                if (Build.VERSION.SDK_INT < 31) throw IllegalStateException()
                a.startActivity(
                    Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                        .putExtra(Intent.EXTRA_COMPONENT_NAME, Words.component(a).flattenToString()),
                )
            } catch (e: Exception) {
                a.start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
    }

    @JavascriptInterface
    fun saveQuiz(score: Int): Int {
        val best = maxOf(score, prefs().getInt("quiz_best", 0))
        prefs().edit().putInt("quiz_best", best).apply()
        return best
    }

    /** What the online layer of the page needs: the saved config and this install's facts. */
    @JavascriptInterface
    fun online(): String {
        val info = a.packageManager.getPackageInfo(a.packageName, 0)
        val days = ((System.currentTimeMillis() - info.firstInstallTime) / 86_400_000L).coerceAtLeast(0)
        val testConfig = Online.isDebuggable(a) && Online.file(a, "online-config-test.json").exists()
        val fresh = testConfig ||
            (Online.lastFetch > 0 && System.currentTimeMillis() - Online.lastFetch < 86_400_000L)
        val dismissed = prefs().getStringSet("dismissed", emptySet()) ?: emptySet()
        return JSONObject()
            .put("config", Online.savedConfig(a) ?: JSONObject.NULL)
            .put("fresh", fresh)
            .put("platform", "android")
            .put("version", info.versionName)
            .put("install_days", days)
            .put("dismissed", JSONArray(dismissed.toList()))
            .toString()
    }

    @JavascriptInterface
    fun dismiss(id: String) {
        if (id.isEmpty() || id.length > 100) return
        val p = prefs()
        val set = (p.getStringSet("dismissed", emptySet()) ?: emptySet()).toMutableSet()
        if (set.size >= 50) set.clear()
        set.add(id)
        p.edit().putStringSet("dismissed", set).apply()
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        if (Online.isSafeUrl(url)) a.start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    @JavascriptInterface
    fun showBanner(provider: String, unitId: String) = a.runOnUiThread { a.ads.show(provider, unitId) }

    @JavascriptInterface
    fun hideBanner() = a.runOnUiThread { if (!a.isDestroyed) a.ads.reset() }
}
