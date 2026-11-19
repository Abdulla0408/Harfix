package uz.harfix.app.keyboard

import android.Manifest
import android.app.Activity
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Pictures in the keyboard's clipboard panel: new screenshots (with the photos permission) and
 * pictures chosen with "Rasm qöşiş". Copies live in cacheDir/clip_images, newest first, and
 * [ClipProvider] lends them to the app being typed in. Nothing leaves the phone.
 */
object ClipImages {
    const val MAX = 6
    /** How fresh a screenshot must be to count as "just taken". */
    const val SHOT_SECONDS = 60L

    val PERMISSION: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    /** A picture chosen with "Rasm qöşiş", to insert when the text field comes back (same process). */
    @Volatile var pending: File? = null
    @Volatile var pendingAt = 0L

    fun dir(ctx: Context) = File(ctx.cacheDir, "clip_images").apply { mkdirs() }

    /** Newest first. */
    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.isFile && !f.name.endsWith(".tmp") }?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun authority(ctx: Context) = "${ctx.packageName}.clips"

    fun uri(ctx: Context, f: File): Uri = Uri.parse("content://${authority(ctx)}/${Uri.encode(f.name)}")

    fun mime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }

    fun extension(mime: String?): String = when (mime?.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }

    /** Copies [src] into the folder as [name] (null if it cannot be read); keeps the newest [MAX]. */
    fun save(ctx: Context, src: Uri, name: String): File? {
        val dir = dir(ctx)
        val out = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        try {
            val copied = ctx.contentResolver.openInputStream(src)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: return null
            if (copied <= 0L || !tmp.renameTo(out)) {
                tmp.delete()
                return null
            }
        } catch (_: Exception) {
            tmp.delete()
            return null
        }
        out.setLastModified(System.currentTimeMillis())
        list(ctx).drop(MAX).forEach { it.delete() }
        return out
    }

    fun clear(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }

    fun canReadShots(ctx: Context): Boolean = ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Is this file in the phone's gallery a screenshot? (by folder or by name) */
    fun isScreenshot(name: String?, path: String?): Boolean =
        path?.contains("screenshot", ignoreCase = true) == true || name?.startsWith("screenshot", ignoreCase = true) == true

    class Shot(val id: Long, val uri: Uri, val name: String, val mime: String?)

    /** The newest screenshot added in the last [SHOT_SECONDS] seconds, or null. */
    fun newestShot(ctx: Context): Shot? {
        val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val since = System.currentTimeMillis() / 1000 - SHOT_SECONDS
        val pathCol = if (Build.VERSION.SDK_INT >= 29) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
        val cols = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE, pathCol)
        return try {
            ctx.contentResolver.query(
                base, cols, "${MediaStore.MediaColumns.DATE_ADDED} >= ?", arrayOf(since.toString()),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n++ < 10) {
                    val name = c.getString(1)
                    if (isScreenshot(name, c.getString(3))) {
                        val id = c.getLong(0)
                        return@use Shot(id, ContentUris.withAppendedId(base, id), name ?: "screenshot", c.getString(2))
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** Lends the clipboard panel's pictures (cacheDir/clip_images) to the app the user types in. */
class ClipProvider : ContentProvider() {
    private fun file(uri: Uri): File {
        val ctx = context ?: throw FileNotFoundException()
        val dir = ClipImages.dir(ctx).canonicalFile
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        val f = File(dir, name).canonicalFile
        if (f.parentFile != dir || !f.isFile) throw FileNotFoundException(uri.toString())
        return f
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = ClipImages.mime(uri.lastPathSegment ?: "")

    override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String> = arrayOf(getType(uri))

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val f = file(uri)
        val cols = (projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
            .filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE || it == MediaStore.MediaColumns.MIME_TYPE }
        return MatrixCursor(cols.toTypedArray()).apply {
            addRow(cols.map { col ->
                when (col) {
                    OpenableColumns.DISPLAY_NAME -> f.name
                    OpenableColumns.SIZE -> f.length()
                    else -> ClipImages.mime(f.name)
                }
            })
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = 0
}

/**
 * "Rasm qöşiş" in the clipboard panel: the keyboard cannot show the photo picker itself, so this
 * see-through screen does it and leaves the picture for the keyboard (same ":keyboard" process).
 * The system picker needs no photos permission.
 */
class PickImageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val pick = if (Build.VERSION.SDK_INT >= 33) {
            Intent(MediaStore.ACTION_PICK_IMAGES)
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(pick, 1)
        } catch (_: Exception) {
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) {
            finish()
            return
        }
        val app = applicationContext
        Thread {
            val type = try {
                app.contentResolver.getType(uri)
            } catch (_: Exception) {
                null
            }
            val f = ClipImages.save(app, uri, "pick_${System.currentTimeMillis()}.${ClipImages.extension(type)}")
            if (f != null) {
                ClipImages.pending = f
                ClipImages.pendingAt = System.currentTimeMillis()
            }
            runOnUiThread { finish() }
        }.start()
    }
}
