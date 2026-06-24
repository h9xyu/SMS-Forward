package com.example.smstopc

import android.content.Context
import android.os.PowerManager
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ForwardHandler {

    private const val WORK_NAME = "process_pending_forwards"

    suspend fun trySend(context: Context, recordId: Long, sender: String, content: String, code: String) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sms_forward:send")
        wakelock.acquire(10_000)
        try {
            val result = EmailSender.send(smsSender = sender, smsContent = content, verificationCode = code)
            if (result.isSuccess) {
                val db = ForwardDatabase.getInstance()
                db.forwardDao().updateResult(recordId, success = true, errorMsg = "")
                AppPreferences.lastForwardTime = System.currentTimeMillis()
                AppPreferences.forwardCount += 1
                return
            }
        } finally {
            wakelock.release()
        }

        // Direct send failed — enqueue WorkManager
        val workRequest = OneTimeWorkRequestBuilder<ForwardWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest
        )
    }
}
