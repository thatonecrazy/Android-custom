package app.veneer.system

import android.accessibilityservice.AccessibilityService
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.veneer.data.IslandConfig
import app.veneer.ui.island.IslandHost
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.roundToInt

/** Things that make the island pop open for a few seconds. */
sealed class IslandEvent {
    data class Charging(val percent: Int) : IslandEvent()
    data class Notification(val app: String, val title: String, val text: String, val icon: Bitmap?, val open: PendingIntent?) : IslandEvent()
}

object IslandEvents {
    private val _events = MutableSharedFlow<IslandEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<IslandEvent> = _events.asSharedFlow()
    fun post(event: IslandEvent) {
        _events.tryEmit(event)
    }
}

/**
 * The window the island lives in. It's an accessibility overlay because that's the only kind of
 * window Android lets an app put above the status bar. It wraps the island tightly, so taps
 * anywhere else go to whatever is underneath.
 */
class IslandOverlay(private val service: AccessibilityService) {
    private val windows = service.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var config: IslandConfig? = null

    fun show(next: IslandConfig) {
        config = next
        val current = view
        if (current != null) {
            runCatching { windows.updateViewLayout(current, params(next)) }
            return
        }
        val o = OverlayOwner().also { it.start() }
        val v = ComposeView(service).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setViewTreeViewModelStoreOwner(o)
            setContent { IslandHost() }
        }
        if (runCatching { windows.addView(v, params(next)) }.isSuccess) {
            view = v
            owner = o
        } else {
            o.stop()
        }
    }

    /** Re-centers on the camera after the screen turns. */
    fun refresh() {
        config?.let { c -> view?.let { runCatching { windows.updateViewLayout(it, params(c)) } } }
    }

    fun hide() {
        view?.let { runCatching { windows.removeView(it) } }
        view = null
        owner?.stop()
        owner = null
    }

    private fun params(c: IslandConfig): WindowManager.LayoutParams {
        val density = service.resources.displayMetrics.density
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = (c.offsetXDp * density).roundToInt() + cameraShift()
            y = (c.offsetYDp * density).roundToInt()
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            title = "Veneer island"
        }
    }

    /** How far the front camera is from the middle of the screen, so the pill sits over it on any phone. */
    private fun cameraShift(): Int {
        if (Build.VERSION.SDK_INT < 30) return 0
        return runCatching {
            val metrics = windows.currentWindowMetrics
            val top = metrics.windowInsets.displayCutout?.boundingRectTop
            if (top == null || top.isEmpty) 0 else top.centerX() - metrics.bounds.width() / 2
        }.getOrDefault(0)
    }
}

/** Gives the overlay's Compose view the lifecycle an Activity normally provides. */
private class OverlayOwner : SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    fun start() {
        saved.performAttach()
        saved.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
