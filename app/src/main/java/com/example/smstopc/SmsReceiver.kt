package com.example.smstopc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!AppPreferences.isEnabled) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: "未知号码"
        val content = messages.joinToString("") { it.messageBody ?: "" }
        if (content.isBlank()) return

        val code = CodeExtractor.extract(content) ?: return
        if (!DedupCache.tryMark(code)) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val db = ForwardDatabase.getInstance()
                val recordId = db.forwardDao().insert(
                    ForwardRecord(sender = sender, content = content, code = code, success = false, pending = true)
                )
                ForwardHandler.trySend(context, recordId, sender, content, code)
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }
}
