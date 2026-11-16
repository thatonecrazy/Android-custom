package app.veneer.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.ViewGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A group of widgets from one app, for the widget picker. */
data class WidgetGroup(val appLabel: String, val packageName: String, val widgets: List<AppWidgetProviderInfo>)

/**
 * Hosts widgets from other apps. Widget views are kept per id so that swiping between pages
 * doesn't rebuild them.
 */
class WidgetHost(private val context: Context) {

    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val host = AppWidgetHost(context, HOST_ID)
    private val views = HashMap<Int, AppWidgetHostView>()

    fun startListening() = runCatching { host.startListening() }.onFailure { Log.w(TAG, "startListening", it) }

    fun stopListening() = runCatching { host.stopListening() }.onFailure { Log.w(TAG, "stopListening", it) }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) {
        views.remove(id)
        runCatching { host.deleteAppWidgetId(id) }
    }

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)

    /** Returns the view for a bound widget, detaching it from any previous parent first. */
    fun view(activityContext: Context, id: Int): AppWidgetHostView? {
        val info = info(id) ?: return null
        val view = views.getOrPut(id) { host.createView(activityContext, id, info) }
        (view.parent as? ViewGroup)?.removeView(view)
        return view
    }

    fun clearViews() = views.clear()

    /** Tells the widget how big it is, so it can pick a layout that fits. */
    fun updateSize(view: AppWidgetHostView, widthDp: Float, heightDp: Float) {
        if (widthDp <= 0f || heightDp <= 0f) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
            } else {
                @Suppress("DEPRECATION")
                view.updateAppWidgetSize(null, widthDp.toInt(), heightDp.toInt(), widthDp.toInt(), heightDp.toInt())
            }
        }
    }

    suspend fun groups(): List<WidgetGroup> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        runCatching { manager.installedProviders }.getOrDefault(emptyList())
            .groupBy { it.provider.packageName }
            .map { (pkg, list) ->
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
                    .getOrDefault(pkg)
                WidgetGroup(label, pkg, list.sortedBy { it.loadLabel(pm).lowercase() })
            }
            .sortedBy { it.appLabel.lowercase() }
    }

    fun label(info: AppWidgetProviderInfo): String = info.loadLabel(context.packageManager)

    fun preview(info: AppWidgetProviderInfo): Drawable? {
        val density = context.resources.displayMetrics.densityDpi
        return runCatching { info.loadPreviewImage(context, density) ?: info.loadIcon(context, density) }.getOrNull()
    }

    fun needsConfiguration(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        if (Build.VERSION.SDK_INT >= 28 &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        ) return false
        return true
    }

    fun isReconfigurable(info: AppWidgetProviderInfo): Boolean =
        info.configure != null && Build.VERSION.SDK_INT >= 28 &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0

    /** Cleans up ids that were allocated but never placed (e.g. after a crash mid-setup). */
    fun deleteOrphans(placed: Set<Int>) {
        runCatching {
            host.appWidgetIds.filter { it !in placed }.forEach { deleteId(it) }
        }
    }

    companion object {
        const val HOST_ID = 0x7e7e
        private const val TAG = "VeneerWidgets"

        fun providerString(info: AppWidgetProviderInfo) = info.provider.flattenToString()

        fun parseProvider(raw: String) = ComponentName.unflattenFromString(raw)
    }
}
