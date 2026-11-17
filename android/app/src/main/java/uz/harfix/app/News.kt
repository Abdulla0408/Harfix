package uz.harfix.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.time.LocalDate

/** Notifications: admin-panel notices, "new version" and "the Gboard feature is off". */
object News {
    const val JOB_ID = 2701
    const val LAPIS = 0xFF1B2F5E.toInt()

    private class Notice(val id: String, val text: String, val url: String?)

    /** Runs in the background twice a day (NewsJob). */
    fun check(context: Context) {
        Online.touch(context)
        val ctx = context.applicationContext
        var cfg = Online.fetch()
        if (cfg == null) {
            cfg = Online.savedConfig(ctx)
        } else {
            try {
                Online.writeAtomic(Online.file(ctx, "online-config.json"), cfg.toString())
                Online.lastFetch = System.currentTimeMillis()
            } catch (_: Exception) {
            }
            Online.applyStoreUrl(ctx, cfg)
        }

        val prefs = context.getSharedPreferences("harfix", 0)
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!prefs.getBoolean("notify", true) || !nm.areNotificationsEnabled()) return

        val notified = (prefs.getStringSet("notified", emptySet()) ?: emptySet()).toMutableSet()

        notice(cfg)?.let { n ->
            if (notified.add("notice:${n.id}")) notify(context, 1, "news", "Harfix", n.text, n.url, null)
        }

        update(context, cfg)?.let { (version, url) ->
            if (notified.add("update:$version")) {
                notify(context, 2, "news", t("Harfix $version chiqdi"), t("Yangi versiyani yuklab oling."), url, null)
            }
        }

        if (!prefs.getBoolean("words_seen", false) || Words.enabled(context)) {
            notified.remove("words-off")
        } else if (notified.add("words-off")) {
            notify(context, 3, "service", t("«Odatiy klaviaturada» oʻchib qolgan"), t("Qayta yoqish uchun bosing."), null, "keyboard")
        }

        if (notified.size > 60) {
            val keep = notified.toList().takeLast(40).toSet()
            notified.retainAll(keep)
        }
        prefs.edit().putStringSet("notified", notified).apply()
    }

    private fun notice(cfg: JSONObject?): Notice? {
        val n = cfg?.optJSONObject("notice") ?: return null
        if (n.opt("enabled") == false) return null
        val id = n.optString("id")
        val text = n.optString("text").trim()
        if (id.isEmpty() || text.isEmpty()) return null
        val platforms = n.optJSONArray("platforms")
        if (platforms != null && platforms.length() > 0 &&
            (0 until platforms.length()).none { platforms.optString(it) == "android" }
        ) return null
        val until = n.optString("until")
        if (until.isNotEmpty() && LocalDate.now().toString() > until) return null
        val url = n.optString("url").takeIf { Online.isSafeUrl(it) }
        return Notice(id, text.take(300), url)
    }

    /** (version, url) if the config announces a newer Android version than this one. */
    private fun update(ctx: Context, cfg: JSONObject?): Pair<String, String>? {
        val a = cfg?.optJSONObject("latest")?.optJSONObject("android") ?: return null
        val version = a.optString("version")
        val url = a.optString("url")
        if (!Online.isSafeUrl(url)) return null
        val mine = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        return if (isNewer(version, mine)) version to url else null
    }

    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".")
        val l = local.split(".")
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrNull(i)?.toIntOrNull() ?: 0
            val b = l.getOrNull(i)?.toIntOrNull() ?: 0
            if (a != b) return a > b
        }
        return false
    }

    fun notify(ctx: Context, id: Int, channel: String, title: String, text: String, url: String?, tab: String?) {
        val intent = if (url == null) {
            Intent(ctx, MainActivity::class.java)
                .putExtra("harfix_open", tab ?: "convert")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }
        val n = Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_harfix)
            .setColor(LAPIS)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(
                PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            .setAutoCancel(true)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}

/** Twice a day, when online: News.check. */
class NewsJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                News.check(applicationContext)
            } catch (_: Exception) {
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
