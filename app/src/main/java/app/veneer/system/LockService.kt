package app.veneer.system

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Exists only so a home screen gesture can lock the phone, the one thing Android reserves for
 * accessibility services. It reads nothing on screen and ignores every event.
 */
class LockService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
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

        /** Locks the screen. Returns false when the service isn't turned on yet. */
        fun lock(): Boolean {
            val service = instance ?: return false
            if (Build.VERSION.SDK_INT < 28) return false
            return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }

        fun openSettings(context: Context) = SystemActions.open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
