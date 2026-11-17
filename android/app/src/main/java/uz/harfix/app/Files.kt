package uz.harfix.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/** Where converted files go: Downloads/Harfix. */
object Files {
    val MIME = mapOf(
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "docm" to "application/vnd.ms-word.document.macroEnabled.12",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "xlsm" to "application/vnd.ms-excel.sheet.macroEnabled.12",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "pptm" to "application/vnd.ms-powerpoint.presentation.macroEnabled.12",
        "txt" to "text/plain",
    )

    /** For the file picker; octet-stream because some file managers report .docx as that. */
    val PICKER_TYPES: Array<String> = (MIME.values + "application/octet-stream").toTypedArray()

    fun mime(name: String): String = MIME[Office.ext(name)] ?: "application/octet-stream"

    /** Android 9 and older keep results in the app's own Downloads folder, served by ResultsProvider. */
    fun legacyDir(ctx: Context): File =
        File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Harfix")

    /** Saves [data] as [name]; returns the uri to open/share and the final file name. */
    fun save(ctx: Context, name: String, data: ByteArray): Pair<Uri, String> {
        if (Build.VERSION.SDK_INT < 29) {
            val dir = legacyDir(ctx)
            dir.mkdirs()
            var file = File(dir, name)
            var n = 2
            val base = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "")
            while (file.exists()) {
                file = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext")
                n++
            }
            file.writeBytes(data)
            val uri = Uri.Builder().scheme("content")
                .authority("${ctx.packageName}.results")
                .appendPath(file.name)
                .build()
            return uri to file.name
        }

        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime(name))
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Harfix")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("insert")
        (resolver.openOutputStream(uri) ?: throw IOException("write")).use { it.write(data) }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        // MediaStore may rename the file ("Hisobot (1).docx"), so read the final name back.
        var finalName = name
        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { finalName = it }
        }
        return uri to finalName
    }
}
