package uz.harfix.app

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityManager

/**
 * "Odatiy klaviaturada" (on any keyboard, e.g. Gboard): oʻ gʻ sh ch become ö ğ ş ç while
 * typing, through an accessibility service, with a Quick Settings tile to turn it on/off.
 *
 * The website APK ("direct" build) ships without this feature so Google Play Protect does
 * not block it; the Play / full build has it. Its code was not in the website APK this
 * project was recovered from, so here the service and tile are only declarations; the
 * screens and settings that use them already handle "not available".
 */
object Words {
    /** Password, visible password, web password / URI, e-mail, web e-mail fields are never touched. */
    val SKIP_VARIATIONS = setOf(128, 144, 224, 16, 32, 208)

    fun component(ctx: Context) = ComponentName(ctx, WordsService::class.java)

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
            TileService.requestListeningState(ctx, ComponentName(ctx, WordsTile::class.java))
        } catch (_: Exception) {
        }
    }
}

/** Declared only in the full build (see [Words]). */
abstract class WordsService : AccessibilityService()

/** Declared only in the full build (see [Words]). */
abstract class WordsTile : TileService()
