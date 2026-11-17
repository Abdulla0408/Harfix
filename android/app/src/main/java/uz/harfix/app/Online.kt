package uz.harfix.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The online layer (same as the Mini App / website): a config with notices, the latest
 * versions and ad settings, read from Supabase (admin panel) with GitHub Pages as a
 * fallback, plus an anonymous daily "I'm alive" ping for the usage counter.
 */
object Online {
    const val SUPABASE = "https://lucopmrphagzxticypmq.supabase.co"
    /** Supabase publishable (public, client-side) key. */
    const val KEY = "sb_publishable_mI40X75CzoVl7fyHziSoDQ_-rLXnRyt"
    const val CONFIG_URL = "$SUPABASE/rest/v1/rpc/harfix_config"
    const val CONFIG_FALLBACK = "https://abdulla0408.github.io/harfix-app/config.json"
    const val PING_URL = "$SUPABASE/rest/v1/rpc/harfix_ping"
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=uz.harfix.app"

    private val SAFE_URL = Regex("^https://[^\\s\"'<>\\\\]+$", RegexOption.IGNORE_CASE)

    val executor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "harfix-online").apply { isDaemon = true } }
    }

    /** When the config was last fetched successfully. */
    @Volatile var lastFetch = 0L
    /** When MainActivity last refreshed the config (at most every 6 hours). */
    @Volatile var lastRefresh = 0L
    /** The ping is tried at most every 30 minutes. */
    @Volatile var nextTouch = 0L

    fun isSafeUrl(url: String?): Boolean = url != null && url.length <= 500 && SAFE_URL.matches(url)

    fun file(ctx: Context, name: String) = File(ctx.filesDir, name)

    fun isDebuggable(ctx: Context) = ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** The last saved config (a debug build prefers online-config-test.json, for testing notices). */
    fun savedConfig(ctx: Context): JSONObject? {
        if (isDebuggable(ctx)) {
            val test = runCatching { file(ctx, "online-config-test.json").readText() }.getOrNull()
            if (test != null) return runCatching { JSONObject(test) }.getOrNull()
        }
        val text = runCatching { file(ctx, "online-config.json").readText() }.getOrNull()
        val cfg = text?.let { runCatching { JSONObject(it) }.getOrNull() }
        applyStoreUrl(ctx, cfg)
        return cfg
    }

    /** Installed from Google Play: the "new version" link goes to Play, not to the APK. */
    fun applyStoreUrl(ctx: Context, cfg: JSONObject?) {
        if (cfg == null || !fromPlay(ctx)) return
        cfg.optJSONObject("latest")?.optJSONObject("android")?.put("url", PLAY_URL)
    }

    fun fromPlay(ctx: Context): Boolean = try {
        val pm = ctx.packageManager
        val installer = if (Build.VERSION.SDK_INT >= 30) {
            pm.getInstallSourceInfo(ctx.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(ctx.packageName)
        }
        installer == "com.android.vending"
    } catch (e: Exception) {
        false
    }

    /** Downloads the config: Supabase first, GitHub Pages if that fails. */
    fun fetch(): JSONObject? {
        val sources = listOf(CONFIG_URL to "{}", CONFIG_FALLBACK to null)
        for ((url, body) in sources) {
            try {
                val (code, text) = http(url, body, if (body != null) KEY else null)
                if (code == 200) return JSONObject(text)
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** Fetches and saves the config; true if it worked. */
    fun refresh(ctx: Context): Boolean {
        val cfg = fetch() ?: return false
        try {
            writeAtomic(file(ctx, "online-config.json"), cfg.toString())
            lastFetch = System.currentTimeMillis()
        } catch (_: Exception) {
        }
        return true
    }

    /** Sends the daily ping soon (at most every 30 minutes). */
    fun touch(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now < nextTouch) return
        nextTouch = now + 30 * 60 * 1000L
        val app = ctx.applicationContext
        executor.execute { ping(app) }
    }

    @Synchronized
    fun id(ctx: Context): String {
        val f = file(ctx, "online-id")
        val saved = runCatching { f.readText() }.getOrNull()?.trim()
        if (saved != null && saved.length == 36) return saved
        val id = UUID.randomUUID().toString()
        writeAtomic(f, id)
        return id
    }

    /** Once a day: anonymous id + platform + version (if the user did not turn statistics off). */
    fun ping(ctx: Context) {
        try {
            @Suppress("DEPRECATION")
            val prefs = ctx.getSharedPreferences("harfix", Context.MODE_MULTI_PROCESS)
            if (!prefs.getBoolean("stats", true)) return
            val today = LocalDate.now().toString()
            val dayFile = file(ctx, "online-ping-day")
            if (runCatching { dayFile.readText() }.getOrNull() == today) return
            val stats = savedConfig(ctx)?.optJSONObject("stats")
            if (stats != null && stats.opt("enabled") == false) return
            var url = PING_URL
            var key = KEY
            val customUrl = stats?.optString("url") ?: ""
            val customKey = (stats?.optString("key") ?: "").trim()
            if (isSafeUrl(customUrl) && customKey.isNotEmpty()) {
                url = customUrl
                key = customKey.take(200)
            }
            val version = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
            val body = JSONObject()
                .put("p_id", id(ctx))
                .put("p_platform", "android")
                .put("p_version", version)
                .toString()
            val code = http(url, body, key).first
            if (code in 200..299) writeAtomic(dayFile, today)
        } catch (_: Exception) {
        }
    }

    /** A small HTTP helper: GET, or POST [body] as JSON with the Supabase [key]. */
    fun http(url: String, body: String?, key: String?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.useCaches = false
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("User-Agent", "Harfix-Android")
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("apikey", key)
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            if (code !in 200..299) return code to ""
            val buf = ByteArrayOutputStream()
            c.inputStream.use { input ->
                val chunk = ByteArray(8192)
                while (buf.size() < 262_144) {
                    val n = input.read(chunk, 0, minOf(8192, 262_144 - buf.size()))
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                }
            }
            return code to String(buf.toByteArray(), Charsets.UTF_8)
        } finally {
            c.disconnect()
        }
    }

    fun writeAtomic(f: File, text: String) {
        val tmp = File(f.path + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(f)
    }
}
