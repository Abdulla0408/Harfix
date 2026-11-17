package uz.harfix.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast

/**
 * "Harfix" in the text selection menu of any app: replaces the selected text with the
 * converted one, or (in read-only text) shows it with Copy / Share.
 */
class ProcessTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Online.touch(this)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        if (text.isNullOrEmpty()) {
            finish()
            return
        }
        val target = Converter.target(getSharedPreferences("harfix", 0).getString("target", "new"))
        val result = Converter.convert(text, target)

        if (!intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)) {
            setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, result))
            finish()
            return
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Harfix")
            .setMessage(result)
            .setPositiveButton(t("Nusxalash")) { _, _ ->
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Harfix", result))
                Toast.makeText(this, t("Nusxalandi"), Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNeutralButton(t("Ulashish")) { _, _ ->
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, result)
                startActivity(Intent.createChooser(send, "Harfix"))
                finish()
            }
            .setNegativeButton(t("Yopish")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .create()
        dialog.show()
        dialog.findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)
    }
}
