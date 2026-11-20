package uz.harfix.app

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityManager

/**
 * "Odatiy klaviaturada" (on any keyboard, e.g. Gboard): oʻ gʻ sh ch become ö ğ ş ç while
 * typing, through an accessibility service, with a Quick Settings tile to turn it on/off.
 *
 * No build ships it now. The website APK ("direct") never had it, so Google Play Protect does not
 * block it; the Google Play build dropped it on 2026-11-20 so its first review needs no
 * Accessibility API declaration (android/PLAY.md). WordsService and WordsTile lived in src/play
 * (with their declarations in src/play/AndroidManifest.xml); to bring them back, restore those
 * files from git. This object names them by class name, so without them there is no
 * accessibility code at all, and the screens show "not available".
 */
object Words {
    /** Password, visible password, web password / URI, e-mail, web e-mail fields are never touched. */
    val SKIP_VARIATIONS = setOf(128, 144, 224, 16, 32, 208)

    fun component(ctx: Context) = ComponentName(ctx.packageName, "uz.harfix.app.WordsService")

    /** Is the service declared in this build? */
    fun available(ctx: Context): Boolean = try {
        ctx.packageManager.getServiceInfo(component(ctx), 0)
        true
    } catch (e: Exception) {
        false
    }

    /** Is the service turned on in Accessibility settings? */
    fun enabled(ctx: Context): Boolean {
        val am = ctx.getSystemService(AccessibilityManager::class.java) ?: return false
        val me = component(ctx)
        return am.getEnabledAccessibilityServiceList(-1).any {
            val si = it.resolveInfo?.serviceInfo
            si != null && ComponentName(si.packageName, si.name) == me
        }
    }

    /** Is it listed in the secure settings (on, even if not running yet)? */
    fun inSecureSettings(ctx: Context): Boolean {
        val list = Settings.Secure.getString(ctx.contentResolver, "enabled_accessibility_services") ?: return false
        val me = component(ctx)
        return list.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun refreshTile(ctx: Context) {
        try {
            TileService.requestListeningState(ctx, ComponentName(ctx.packageName, "uz.harfix.app.WordsTile"))
        } catch (_: Exception) {
        }
    }
}
