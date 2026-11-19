package uz.harfix.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile "Harfix": turns "Odatiy klaviaturada" on and off with one tap. When the
 * service itself is not switched on in Accessibility settings, the tap opens the app, which
 * explains it and leads there.
 */
class WordsTile : TileService() {
    private fun prefs() = getSharedPreferences("harfix", MODE_PRIVATE)

    override fun onStartListening() = paint()

    override fun onClick() {
        if (!Words.enabled(this)) {
            openApp()
            return
        }
        val p = prefs()
        p.edit().putBoolean("words", !p.getBoolean("words", true)).apply()
        paint()
    }

    private fun paint() {
        val tile = qsTile ?: return
        val ready = Words.enabled(this)
        val on = ready && prefs().getBoolean("words", true)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.words_tile)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = getString(
                when {
                    !ready -> R.string.words_tile_setup
                    on -> R.string.words_tile_on
                    else -> R.string.words_tile_off
                },
            )
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
