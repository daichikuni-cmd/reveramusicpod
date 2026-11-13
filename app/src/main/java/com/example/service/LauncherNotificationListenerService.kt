package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LauncherNotificationListenerService : NotificationListenerService() {

    companion object {
        private val _activeNotificationCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val activeNotificationCounts: StateFlow<Map<String, Int>> = _activeNotificationCounts.asStateFlow()

        var isServiceConnected: Boolean = false
            private set

        private var instance: LauncherNotificationListenerService? = null

        fun refreshCounts() {
            instance?.updateCounts()
        }

        fun requestRebindService(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    requestRebind(ComponentName(context, LauncherNotificationListenerService::class.java))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        isServiceConnected = true
        updateCounts()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) instance = null
        isServiceConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        updateCounts()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        updateCounts()
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        super.onNotificationRankingUpdate(rankingMap)
        updateCounts()
    }

    fun updateCounts() {
        try {
            val active = activeNotifications ?: return
            val counts = mutableMapOf<String, Int>()

            // Group notifications by package name
            val packageGroups = active.filterNotNull().groupBy { it.packageName ?: "" }

            for ((pkg, sbns) in packageGroups) {
                if (pkg.isBlank() || pkg == "android" || pkg == "com.android.systemui") continue

                // Filter out non-dismissible ongoing notifications (e.g. background media players, foreground services)
                val validNotifications = sbns.filter { sbn ->
                    val notif = sbn.notification ?: return@filter false
                    val isOngoing = sbn.isOngoing || (notif.flags and Notification.FLAG_ONGOING_EVENT != 0)
                    !isOngoing
                }

                if (validNotifications.isEmpty()) continue

                // Sub-group notifications by group key if grouped
                val groupKeyMap = validNotifications.groupBy { it.groupKey ?: "${it.id}_${it.tag}" }
                var totalPkgCount = 0

                for ((_, groupSbns) in groupKeyMap) {
                    val summarySbn = groupSbns.firstOrNull { sbn ->
                        val flags = sbn.notification?.flags ?: 0
                        (flags and Notification.FLAG_GROUP_SUMMARY) != 0
                    }
                    val childSbns = groupSbns.filter { sbn ->
                        val flags = sbn.notification?.flags ?: 0
                        (flags and Notification.FLAG_GROUP_SUMMARY) == 0
                    }

                    if (childSbns.isNotEmpty()) {
                        // Sum up the individual child notifications
                        val childrenSum = childSbns.sumOf { extractCountFromSbn(it) }
                        val summaryCount = summarySbn?.let { extractCountFromSbn(it) } ?: 0
                        totalPkgCount += maxOf(childrenSum, summaryCount)
                    } else if (summarySbn != null) {
                        // Only summary notification exists
                        totalPkgCount += extractCountFromSbn(summarySbn)
                    } else {
                        // Fallback sum
                        totalPkgCount += groupSbns.sumOf { extractCountFromSbn(it) }
                    }
                }

                if (totalPkgCount > 0) {
                    counts[pkg] = totalPkgCount
                }
            }

            _activeNotificationCounts.value = counts
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun extractCountFromSbn(sbn: StatusBarNotification): Int {
        val notif = sbn.notification ?: return 1
        val extras = notif.extras

        // 1. Check explicit unread count fields in extras (LINE, Samsung, Xiaomi, standard Android)
        if (extras != null) {
            val unreadCount = extras.getInt("android.unread_count", 0)
            if (unreadCount > 0) return unreadCount

            val convCount = extras.getInt("android.conversationUnreadMessageCount", 0)
            if (convCount > 0) return convCount

            val badgeCount = extras.getInt("badge_count", 0)
            if (badgeCount > 0) return badgeCount

            val notificationCount = extras.getInt("count", 0)
            if (notificationCount > 0) return notificationCount

            val lineCount = extras.getInt("line_count", 0)
            if (lineCount > 0) return lineCount
        }

        // 2. Check MessagingStyle message items (Messaging Style notifications e.g. Messenger, LINE, WhatsApp)
        if (extras != null) {
            try {
                val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                if (!messages.isNullOrEmpty()) {
                    return messages.size
                }
            } catch (e: Throwable) {
                // Ignore
            }

            try {
                val messageList = extras.getCharSequenceArrayList("android.messages")
                if (!messageList.isNullOrEmpty()) {
                    return messageList.size
                }
            } catch (e: Throwable) {
                // Ignore
            }
        }

        // 3. Check InboxStyle lines (Gmail, Outlook, notifications with multiple lines of items)
        if (extras != null) {
            try {
                val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                if (!textLines.isNullOrEmpty()) {
                    return textLines.size
                }
            } catch (e: Throwable) {
                // Ignore
            }
        }

        // 4. Check standard notification.number
        if (notif.number > 0) {
            return notif.number
        }

        // 5. Default 1 for a single active notification
        return 1
    }
}
