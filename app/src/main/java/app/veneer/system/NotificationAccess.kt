package app.veneer.system

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import app.veneer.veneer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notification access powers two things: dots on app icons and the music widget (Android only
 * shares media sessions with apps that have notification access). Nothing is stored or sent anywhere.
 */
class NotificationDotsService : NotificationListenerService() {

    override fun onListenerConnected() {
        connected.value = true
        recount()
    }

    override fun onListenerDisconnected() {
        connected.value = false
        _counts.value = emptyMap()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        recount()
        if (sbn != null) runCatching { announce(sbn) }
    }

    private val lastAnnounced = HashMap<String, Long>()

    /** Shows a new notification in the island, if the island is on and the notification is worth a pop-up. */
    private fun announce(sbn: StatusBarNotification) {
        val island = veneer.store.current.island
        if (!island.enabled || !island.notifications) return
        val n = sbn.notification
        if (sbn.packageName == packageName || sbn.isOngoing || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.category in setOf(Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_PROGRESS, Notification.CATEGORY_SERVICE, Notification.CATEGORY_SYSTEM)) return
        // Quiet notifications, and ones blocked by Do Not Disturb, stay quiet here too.
        val ranking = Ranking()
        if (currentRanking?.getRanking(sbn.key, ranking) == true) {
            if (ranking.importance < NotificationManager.IMPORTANCE_DEFAULT || !ranking.matchesInterruptionFilter()) return
        }
        // Android posts a notification again every time it updates; only new ones pop up.
        val now = System.currentTimeMillis()
        if (now - sbn.postTime > 5_000) return
        val last = lastAnnounced[sbn.key]
        if (last != null && (now - last < 15_000 || n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)) return
        lastAnnounced[sbn.key] = now
        if (lastAnnounced.size > 200) lastAnnounced.entries.removeAll { now - it.value > 60_000 }

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }
            .getOrDefault(sbn.packageName)
        val icon = runCatching { packageManager.getApplicationIcon(sbn.packageName).toBitmap(96, 96) }.getOrNull()
        // On the lock screen, say only that something arrived.
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        IslandEvents.post(
            IslandEvent.Notification(label, if (locked) "New notification" else title, if (locked) "" else text, icon, n.contentIntent),
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = recount()

    private fun recount() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val users = getSystemService(UserManager::class.java)
        val ranking = Ranking()
        val counts = HashMap<String, Int>()
        for (sbn in active) {
            val n = sbn.notification
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            if (sbn.isOngoing) continue
            val rankings = currentRanking
            if (rankings != null && rankings.getRanking(sbn.key, ranking) && !ranking.canShowBadge()) continue
            val serial = users.getSerialNumberForUser(sbn.user)
            val key = "${sbn.packageName}#$serial"
            counts[key] = (counts[key] ?: 0) + 1
        }
        _counts.value = counts
    }

    companion object {
        private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())

        /** Notification counts keyed by "package#userSerial". */
        val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()
        val connected = MutableStateFlow(false)

        fun component(context: Context) = ComponentName(context, NotificationDotsService::class.java)

        fun isAllowed(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        /** Opens the system page where the user can give Veneer notification access. */
        fun requestAccess(context: Context) {
            val detail = if (Build.VERSION.SDK_INT >= 30) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
            } else null
            if (detail == null || !SystemActions.open(context, detail, quiet = true)) {
                SystemActions.open(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
    }
}
