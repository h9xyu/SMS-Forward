package com.example.smstopc

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SmsNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!AppPreferences.isEnabled) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getString(Notification.EXTRA_SUB_TEXT) ?: ""
        val fullText = "$title $text"
        if (fullText.isBlank()) return

        val sender = when {
            subText.contains(Regex("""\d{5,}""")) -> subText
            title.contains(Regex("""\d{5,}""")) -> title
            subText.isNotBlank() -> "$subText $title"
            else -> title
        }

        val code = CodeExtractor.extract(fullText) ?: return
        if (!DedupCache.tryMark(code)) return

        scope.launch {
            try {
                val db = ForwardDatabase.getInstance()
                val recordId = db.forwardDao().insert(
                    ForwardRecord(sender = sender, content = fullText, code = code, success = false, pending = true)
                )
                ForwardHandler.trySend(this@SmsNotificationListener, recordId, sender, fullText, code)
            } catch (_: Exception) {
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
    override fun onListenerConnected() {}
}
