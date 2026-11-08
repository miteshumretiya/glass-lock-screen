package com.mitesh.glasslock

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LockNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val icon: ImageBitmap?,
    val title: String,
    val text: String,
    val postTime: Long,
    val contentIntent: PendingIntent?,
    val clearable: Boolean,
    val autoCancel: Boolean,
)

/** Reads the notifications that should appear on the glass lock screen. */
class NotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        itemsFlow.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = refresh()

    private fun refresh() {
        val active: Array<StatusBarNotification> = try {
            activeNotifications
        } catch (e: Exception) {
            null
        } ?: return
        val ranking: RankingMap? = try {
            currentRanking
        } catch (e: Exception) {
            null
        }
        itemsFlow.value = active.asSequence()
            .filter { keep(it, ranking) }
            .sortedByDescending { it.postTime }
            .take(40)
            .map { toModel(it) }
            .toList()
    }

    private fun keep(sbn: StatusBarNotification, ranking: RankingMap?): Boolean {
        if (sbn.packageName == packageName) return false
        val n: Notification = sbn.notification ?: return false
        if (sbn.isOngoing) return false
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return false
        if ((n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return false
        if (n.visibility == Notification.VISIBILITY_SECRET) return false
        if (ranking != null) {
            val r = Ranking()
            if (ranking.getRanking(sbn.key, r)) {
                if (r.importance <= NotificationManager.IMPORTANCE_MIN) return false
                if (r.lockscreenVisibilityOverride == Notification.VISIBILITY_SECRET) return false
            }
        }
        val extras = n.extras ?: return false
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)
        return !(title.isNullOrBlank() && text.isNullOrBlank())
    }

    private fun toModel(sbn: StatusBarNotification): LockNotification {
        val n = sbn.notification
        val extras = n.extras
        val title = (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val app = appInfo(sbn.packageName)
        return LockNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = app.first,
            icon = app.second,
            title = title.trim(),
            text = text.trim().replace('\n', ' '),
            postTime = sbn.postTime,
            contentIntent = n.contentIntent,
            clearable = sbn.isClearable,
            autoCancel = (n.flags and Notification.FLAG_AUTO_CANCEL) != 0,
        )
    }

    private fun appInfo(pkg: String): Pair<String, ImageBitmap?> = synchronized(cache) {
        cache.getOrPut(pkg) {
            try {
                val pm = packageManager
                @Suppress("DEPRECATION")
                val info = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(info).toString()
                val icon = pm.getApplicationIcon(info).toBitmap(108, 108).asImageBitmap()
                label to icon
            } catch (e: Exception) {
                pkg to null
            }
        }
    }

    companion object {
        private val itemsFlow = MutableStateFlow<List<LockNotification>>(emptyList())
        val items: StateFlow<List<LockNotification>> = itemsFlow.asStateFlow()

        @Volatile
        private var instance: NotificationService? = null
        private val cache = HashMap<String, Pair<String, ImageBitmap?>>()

        fun component(context: Context) = ComponentName(context, NotificationService::class.java)

        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun cancel(key: String) {
            try {
                instance?.cancelNotification(key)
            } catch (e: Exception) {
                // Listener not connected; nothing to clear.
            }
        }
    }
}
