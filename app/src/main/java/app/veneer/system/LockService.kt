package app.veneer.system

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import app.veneer.veneer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Does the two things Android reserves for accessibility services: locking the phone from a home
 * screen gesture, and drawing the island above the status bar. It reads nothing on screen and
 * ignores every event.
 */
class LockService : AccessibilityService() {

    private var scope: CoroutineScope? = null
    private var island: IslandOverlay? = null

    override fun onServiceConnected() {
        instance = this
        scope?.cancel()
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope
        val overlay = IslandOverlay(this, serviceScope)
        island = overlay
        serviceScope.apply {
            launch {
                veneer.store.state.map { it.island }.distinctUntilChanged().collect { config ->
                    if (config.enabled) overlay.show(config) else overlay.hide()
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        island?.refresh()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutDown()
        super.onDestroy()
    }

    private fun shutDown() {
        instance = null
        scope?.cancel()
        scope = null
        island?.hide()
        island = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: LockService? = null

        val supported: Boolean get() = Build.VERSION.SDK_INT >= 28

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(context, LockService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /** Whether the service is running right now (turned on and connected). */
        val running: Boolean get() = instance != null

        /** Runs one of Android's global actions (screenshot, power menu, ...). False when the service isn't running. */
        fun globalAction(action: Int): Boolean = instance?.performGlobalAction(action) == true

        /** Locks the screen. Returns false when the service isn't turned on yet. */
        fun lock(): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT < 28) return false
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        fun openSettings(context: Context) = SystemActions.open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
