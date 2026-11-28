package app.veneer.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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

/** Whether the quick actions drop-down is open. The island and its window both follow this. */
object IslandPanel {
    val open = MutableStateFlow(false)

    fun close() {
        open.value = false
    }
}

/** The island window's flags: never steals taps meant for other apps, and hears about taps outside it. */
internal fun islandWindowFlags(panelOpen: Boolean): Int {
    var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    // Only while the drop-down is open does the window take focus, so Back can close it.
    if (!panelOpen) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    return flags
}

/**
 * Holds the island's Compose view. Catches the two things Compose can't see on its own: a tap
 * outside the window (ACTION_OUTSIDE, from FLAG_WATCH_OUTSIDE_TOUCH) and the Back key.
 */
internal class IslandFrame(context: Context) : FrameLayout(context) {
    var onOutside: (() -> Unit)? = null
    var onBack: (() -> Unit)? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutside?.invoke()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && onBack != null) {
            if (event.action == KeyEvent.ACTION_UP) onBack?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
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
class IslandOverlay(private val service: AccessibilityService, private val scope: CoroutineScope) {
    private val windows = service.getSystemService(WindowManager::class.java)
    private var view: IslandFrame? = null
    private var owner: OverlayOwner? = null
    private var config: IslandConfig? = null
    private var panelJob: Job? = null

    fun show(next: IslandConfig) {
        config = next
        val current = view
        if (current != null) {
            runCatching { windows.updateViewLayout(current, params(next)) }
            return
        }
        val o = OverlayOwner().also { it.start() }
        val compose = ComposeView(service).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setViewTreeViewModelStoreOwner(o)
            setContent { IslandHost() }
        }
        val frame = IslandFrame(service).apply {
            addView(compose)
            // A tap anywhere else, or Back, closes the drop-down. The tap still reaches the app underneath.
            onOutside = { IslandPanel.close() }
            onBack = { IslandPanel.close() }
        }
        if (runCatching { windows.addView(frame, params(next)) }.isSuccess) {
            view = frame
            owner = o
            panelJob = scope.launch {
                IslandPanel.open.collect { refresh() }
            }
        } else {
            o.stop()
        }
    }

    /** Re-centers on the camera after the screen turns, and follows the drop-down opening and closing. */
    fun refresh() {
        config?.let { c -> view?.let { runCatching { windows.updateViewLayout(it, params(c)) } } }
    }

    fun hide() {
        panelJob?.cancel()
        panelJob = null
        IslandPanel.close()
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
            islandWindowFlags(IslandPanel.open.value),
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
