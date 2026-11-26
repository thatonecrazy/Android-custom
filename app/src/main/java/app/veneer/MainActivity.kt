package app.veneer

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Rect
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import app.veneer.apps.AppKey
import app.veneer.ui.launcher.HomeController
import app.veneer.ui.launcher.LauncherActions
import app.veneer.ui.launcher.LauncherRoot
import app.veneer.ui.launcher.widgetSpan
import app.veneer.widgets.WidgetHost

/** The home screen. */
class MainActivity : ComponentActivity(), LauncherActions {

    override lateinit var widgetHost: WidgetHost
        private set
    private lateinit var controller: HomeController

    /** A widget id that has been allocated but not placed yet (waiting on bind or configure). */
    private var pendingWidgetId = -1

    /** The stack the pending widget goes into, or -1 for the home screen. */
    private var pendingStack = -1L

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingWidgetId
        if (id == -1) return@registerForActivityResult
        val info = widgetHost.info(id)
        if (result.resultCode == RESULT_OK && info != null) configureOrPlace(id, info) else abandonPending()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        pendingWidgetId = savedInstanceState?.getInt(KEY_PENDING_WIDGET, -1) ?: -1
        pendingStack = savedInstanceState?.getLong(KEY_PENDING_STACK, -1L) ?: -1L
        widgetHost = WidgetHost(this)
        controller = HomeController(veneer, ViewConfiguration.get(this).scaledTouchSlop.toFloat())
        if (savedInstanceState == null && pendingWidgetId == -1) {
            val placed = veneer.store.current.items.flatMap { HomeController.widgetIds(it.content) }.toSet()
            widgetHost.deleteOrphans(placed)
        }
        setContent { LauncherRoot(controller, this) }
    }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgetHost.stopListening()
        controller.cancelDrag()
    }

    override fun onDestroy() {
        super.onDestroy()
        widgetHost.clearViews()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PENDING_WIDGET, pendingWidgetId)
        outState.putLong(KEY_PENDING_STACK, pendingStack)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            val alreadyOnHome = hasWindowFocus() &&
                (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
            controller.onHomePressed(alreadyOnHome)
        }
    }

    override fun launch(key: AppKey, bounds: Rect?) {
        val options = bounds?.takeIf { !it.isEmpty }?.let {
            ActivityOptions.makeClipRevealAnimation(window.decorView, it.left, it.top, it.width(), it.height()).toBundle()
        }
        veneer.apps.launch(key, bounds, options)
    }

    override fun addSystemWidget(info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateId()
        pendingWidgetId = id
        pendingStack = controller.stackTargetId ?: -1L
        if (widgetHost.bindIfAllowed(id, info)) {
            configureOrPlace(id, info)
        } else {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            runCatching { bindWidget.launch(intent) }.onFailure { abandonPending() }
        }
    }

    private fun configureOrPlace(id: Int, info: AppWidgetProviderInfo) {
        if (widgetHost.needsConfiguration(info)) {
            runCatching {
                widgetHost.host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
            }.onFailure { place(id, info) }
        } else {
            place(id, info)
        }
    }

    private fun place(id: Int, info: AppWidgetProviderInfo) {
        val t = veneer.store.current.theme
        val bounds = controller.gridBounds
        val (sx, sy) = widgetSpan(info, bounds.width / t.gridColumns, bounds.height / t.gridRows, t.gridColumns, t.gridRows)
        controller.addSystemWidget(id, WidgetHost.providerString(info), sx, sy, pendingStack.takeIf { it >= 0 })
        pendingWidgetId = -1
        pendingStack = -1L
    }

    private fun abandonPending() {
        if (pendingWidgetId != -1) widgetHost.deleteId(pendingWidgetId)
        pendingWidgetId = -1
        pendingStack = -1L
    }

    @Deprecated("Widget configuration still reports back through onActivityResult")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CONFIGURE) {
            val id = pendingWidgetId
            if (id == -1) return
            val info = widgetHost.info(id)
            if (resultCode == RESULT_OK && info != null) place(id, info) else abandonPending()
        }
    }

    override fun reconfigureWidget(appWidgetId: Int) {
        runCatching {
            widgetHost.host.startAppWidgetConfigureActivityForResult(this, appWidgetId, 0, REQ_RECONFIGURE, null)
        }
    }

    override fun deleteWidget(appWidgetId: Int) = widgetHost.deleteId(appWidgetId)

    override fun openCustomize(section: String?) {
        startActivity(Intent(this, CustomizeActivity::class.java).putExtra(CustomizeActivity.EXTRA_SECTION, section))
    }

    private companion object {
        const val KEY_PENDING_WIDGET = "pending_widget"
        const val KEY_PENDING_STACK = "pending_stack"
        const val REQ_CONFIGURE = 1201
        const val REQ_RECONFIGURE = 1202
    }
}
