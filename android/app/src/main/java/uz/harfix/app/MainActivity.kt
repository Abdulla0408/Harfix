package uz.harfix.app

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import org.json.JSONArray
import org.json.JSONObject
import uz.harfix.app.keyboard.ClipImages
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The whole app UI is a web page (assets/ui, the same app as the Telegram Mini App);
 * this activity hosts it and does the phone-side work through [Bridge].
 */
class MainActivity : Activity() {
    lateinit var web: WebView
        private set
    lateinit var ads: BannerAds
        private set
    /** Events sent before the page said ready() wait here. */
    private val queue = ArrayList<String>()
    private var pageReady = false
    val worker: ExecutorService = Executors.newSingleThreadExecutor()

    private val night: Boolean
        get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = if (night) 0xFF111418.toInt() else 0xFFF1F3F6.toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(bg)

        web = WebView(this).apply {
            setBackgroundColor(bg)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.setSupportZoom(false)
            isVerticalScrollBarEnabled = false
            addJavascriptInterface(Bridge(this@MainActivity), "HarfixAndroid")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url
                    if (url.scheme == "file") return false
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                    } catch (_: ActivityNotFoundException) {
                    }
                    return true
                }
            }
        }
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val adBox = FrameLayout(this)
        adBox.visibility = android.view.View.GONE
        root.addView(adBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        ads = BannerAds(this, adBox)
        setContentView(root)

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { v, insets ->
                val i = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.ime() or WindowInsets.Type.displayCutout(),
                )
                v.setPadding(i.left, i.top, i.right, i.bottom)
                ads.imeVisible = insets.isVisible(WindowInsets.Type.ime())
                ads.update()
                WindowInsets.CONSUMED
            }
            val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (night) 0 else light, light)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { back() }
        }

        web.loadUrl("file:///android_asset/ui/index.html")

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("news", t("Yangiliklar va yangi versiya"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = t("Harfix eʼlonlari va yangi versiya chiqqanda")
            },
        )
        nm.createNotificationChannel(
            NotificationChannel("service", t("Harfix holati"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = t("«Odatiy klaviaturada» oʻchib qolsa")
            },
        )

        val js = getSystemService(JobScheduler::class.java)
        if (js.getPendingJob(News.JOB_ID) == null) {
            js.schedule(
                JobInfo.Builder(News.JOB_ID, ComponentName(this, NewsJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(12 * 60 * 60 * 1000L)
                    .setPersisted(true)
                    .build(),
            )
        }

        handleIntent(intent)
    }

    // --- page <-> phone ---------------------------------------------------------------------

    /** The page loaded and installed window.harfixEvent: send what waited. */
    fun onPageReady() {
        pageReady = true
        for (js in queue) web.evaluateJavascript(js, null)
        queue.clear()
        if (Build.VERSION.SDK_INT >= 33) {
            val asked = getSharedPreferences("harfix", 0).getBoolean("notify_asked", false)
            if (!asked && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                web.postDelayed({ if (!isDestroyed) askNotifications() }, 1500)
            }
        }
    }

    /** Sends an event to window.harfixEvent. */
    fun send(event: JSONObject) {
        val js = "window.harfixEvent && window.harfixEvent($event)"
        runOnUiThread {
            if (pageReady) web.evaluateJavascript(js, null) else queue.add(js)
        }
    }

    private fun sendState() = send(JSONObject().put("kind", "state"))

    fun toast(text: String) = runOnUiThread { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }

    fun start(intent: Intent) = runOnUiThread {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(t("Buni ochadigan ilova topilmadi"))
        } catch (_: SecurityException) {
            toast(t("Fayl topilmadi: u oʻchirilgan yoki koʻchirilgan boʻlishi mumkin"))
        }
    }

    private fun back() {
        web.evaluateJavascript("window.harfixBack ? harfixBack() : false") { handled ->
            if (handled != "true") finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = back()

    fun askNotifications() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.areNotificationsEnabled()) return
        val prefs = getSharedPreferences("harfix", 0)
        if (Build.VERSION.SDK_INT < 33 ||
            (prefs.getBoolean("notify_asked", false) && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
        ) {
            start(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        } else {
            prefs.edit().putBoolean("notify_asked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
        }
    }

    // --- files ------------------------------------------------------------------------------

    fun pickFiles() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, Files.PICKER_TYPES)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_PICK)
        } catch (_: ActivityNotFoundException) {
            toast(t("Fayl tanlash oynasi topilmadi"))
        }
    }

    // --- keyboard: screenshots in the clipboard panel ------------------------------------------

    /** "Skrinshotlar buferda": the keyboard needs the photos permission to see new screenshots. */
    fun askShots() {
        if (!ClipImages.shotsSupported(this)) return // the Google Play build has no photos permission
        @Suppress("DEPRECATION")
        getSharedPreferences("harfix", Context.MODE_MULTI_PROCESS).edit().putBoolean("shots", true).apply()
        if (checkSelfPermission(ClipImages.PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            if (pageReady) sendState()
            return
        }
        requestPermissions(arrayOf(ClipImages.PERMISSION), REQ_SHOTS)
    }

    // --- keyboard background ------------------------------------------------------------------

    /** The picture behind the keyboard (the keyboard reads it from here). */
    fun keyboardBackground() = java.io.File(filesDir, "keyboard_bg.jpg")

    /** Opens the system photo picker (no photo permission needed). */
    fun pickBackground() {
        val i = if (Build.VERSION.SDK_INT >= 33) {
            Intent(android.provider.MediaStore.ACTION_PICK_IMAGES)
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_BG)
        } catch (_: ActivityNotFoundException) {
            toast(t("Rasm tanlash oynasi topilmadi"))
        }
    }

    /** Saves the chosen picture, upright and at most 1280 px, and switches the keyboard to it. */
    private fun saveBackground(uri: Uri) {
        val ok = try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
                ?: throw IOException("not a picture")
            val turn = try {
                contentResolver.openInputStream(uri)?.use {
                    when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (_: Exception) {
                0f
            }
            val scale = minOf(1f, 1280f / maxOf(bmp.width, bmp.height))
            if (turn != 0f || scale < 1f) {
                val m = android.graphics.Matrix().apply { postScale(scale, scale); postRotate(turn) }
                bmp = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            val tmp = java.io.File(filesDir, "keyboard_bg.tmp")
            tmp.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
            if (!tmp.renameTo(keyboardBackground())) throw IOException("rename")
            @Suppress("DEPRECATION")
            getSharedPreferences("harfix", Context.MODE_MULTI_PROCESS).edit().putInt("theme", Bridge.PHOTO_THEME).apply()
            true
        } catch (_: Throwable) {
            false
        }
        toast(t(if (ok) "Klaviatura foni oʻrnatildi" else "Bu rasmni ochib boʻlmadi"))
        runOnUiThread { if (pageReady) sendState() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_BG) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) worker.execute { saveBackground(uri) }
            return
        }
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) return
        val uris = ArrayList<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
        } else {
            data.data?.let { uris.add(it) }
        }
        if (uris.isNotEmpty()) worker.execute { describeFiles(uris) }
    }

    /** Tells the page the name, size and support of each chosen/shared file. */
    private fun describeFiles(uris: List<Uri>) {
        val items = JSONArray()
        for (uri in uris) {
            var name: String? = null
            var size = -1L
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        name = c.getString(0)
                        if (!c.isNull(1)) size = c.getLong(1)
                    }
                }
            } catch (_: Exception) {
            }
            val n = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "fayl"
            items.put(
                JSONObject()
                    .put("uri", uri.toString())
                    .put("name", n)
                    .put("size", size)
                    .put("supported", Office.ext(n) in Office.SUPPORTED),
            )
        }
        send(JSONObject().put("kind", "files").put("items", items))
    }

    /** Converts the files one by one, reporting progress to the page (worker thread). */
    fun convertFiles(items: JSONArray, target: String) {
        var ok = 0
        var failed = 0
        for (i in 0 until items.length()) {
            send(JSONObject().put("kind", "file").put("index", i).put("status", "working"))
            val item = items.getJSONObject(i)
            val name = item.getString("name")
            val r = JSONObject()
            when {
                Office.ext(name) !in Office.SUPPORTED -> r.put("ok", false).put("error", "unsupported")
                item.optLong("size", -1) > MAX_FILE -> r.put("ok", false).put("error", "too_big")
                else -> try {
                    val input = contentResolver.openInputStream(Uri.parse(item.getString("uri")))
                    if (input == null) {
                        r.put("ok", false).put("error", "io")
                    } else {
                        val data = input.use { it.readBytes() }
                        val (uri, saved) = Files.save(this, Office.outputName(name, target), Office.convert(data, name, target))
                        r.put("ok", true).put("uri", uri.toString()).put("name", saved)
                    }
                } catch (e: Office.Unsupported) {
                    r.put("ok", false).put("error", "unsupported")
                } catch (e: SecurityException) {
                    r.put("ok", false).put("error", "io")
                } catch (e: IOException) {
                    r.put("ok", false).put("error", "io")
                } catch (e: OutOfMemoryError) {
                    r.put("ok", false).put("error", "too_big")
                } catch (e: Exception) {
                    r.put("ok", false).put("error", "failed")
                }
            }
            if (r.getBoolean("ok")) ok++ else failed++
            r.put("kind", "file").put("index", i).put("status", if (r.getBoolean("ok")) "done" else "error")
            send(r)
        }
        send(JSONObject().put("kind", "finished").put("ok", ok).put("failed", failed))
    }

    // --- intents: share text/files to Harfix, open .docx with Harfix, notifications -----------

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        intent.getStringExtra("harfix_open")?.let { send(JSONObject().put("kind", "open").put("tab", it)) }
        if (intent.getStringExtra("harfix_action") == "shots") {
            intent.removeExtra("harfix_action")
            send(JSONObject().put("kind", "open").put("tab", "keyboard"))
            askShots()
        }
        val uris = ArrayList<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val stream: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                stream?.let { uris.add(it) }
                if (uris.isEmpty() && !text.isNullOrEmpty()) send(JSONObject().put("kind", "text").put("text", text))
            }
            Intent.ACTION_VIEW -> intent.data?.let { uris.add(it) }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list: List<Uri>? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                list?.let { uris.addAll(it) }
            }
        }
        if (uris.isNotEmpty()) worker.execute { describeFiles(uris) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    // --- lifecycle ----------------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        if (pageReady) sendState()
        // the online config, at most every 6 hours while the app is used
        val now = System.currentTimeMillis()
        if (now - Online.lastRefresh >= 6 * 60 * 60 * 1000L) {
            Online.lastRefresh = now
            val app: Context = applicationContext
            Online.executor.execute {
                val ok = Online.refresh(app)
                if (ok) send(JSONObject().put("kind", "online"))
                Online.ping(app)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pageReady) sendState()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFY && pageReady) sendState()
        if (requestCode == REQ_SHOTS) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                toast(t("Tayyor: endi skrinshotlar klaviatura buferida turadi"))
            } else if (!shouldShowRequestPermissionRationale(ClipImages.PERMISSION)) {
                // "don't ask again": only Android settings can give it now
                toast(t("Sozlamalar → Ruxsatlar → Rasmlar: ruxsat bering"))
                start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            }
            if (pageReady) sendState()
        }
    }

    override fun onDestroy() {
        ads.reset()
        worker.shutdownNow()
        web.destroy()
        super.onDestroy()
    }

    companion object {
        const val REQ_PICK = 7
        const val REQ_NOTIFY = 8
        const val REQ_BG = 9
        const val REQ_SHOTS = 10
        /** 60 MB */
        const val MAX_FILE = 62_914_560L
    }
}
