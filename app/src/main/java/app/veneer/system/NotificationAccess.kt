package app.veneer.system

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
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

    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()

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
