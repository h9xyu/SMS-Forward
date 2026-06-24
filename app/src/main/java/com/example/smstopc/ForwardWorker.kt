package com.example.smstopc

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ForwardWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = ForwardDatabase.getInstance()
        val records = withContext(Dispatchers.IO) {
            db.forwardDao().getPending()
        }

        var allSuccess = true
        for (record in records) {
            var result = EmailSender.send(
                smsSender = record.sender,
                smsContent = record.content,
                verificationCode = record.code
            )

            if (!result.isSuccess) {
                delay(2000)
                result = EmailSender.send(
                    smsSender = record.sender,
                    smsContent = record.content,
                    verificationCode = record.code
                )
            }

            val success = result.isSuccess
            val errorMsg = if (success) "" else (result.exceptionOrNull()?.message ?: "发送失败")

            withContext(Dispatchers.IO) {
                db.forwardDao().updateResult(record.id, success, errorMsg)
            }

            if (success) {
                AppPreferences.lastForwardTime = System.currentTimeMillis()
                AppPreferences.forwardCount += 1
                showNotification("验证码 ${record.code} 已转发")
            } else {
                allSuccess = false
            }
        }

        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000
            db.forwardDao().deleteOlderThan(cutoff)
        }

        return if (allSuccess) Result.success() else Result.retry()
    }

    private fun showNotification(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val notification = NotificationCompat.Builder(applicationContext, App.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("短信转发")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    Intent(applicationContext, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

        NotificationManagerCompat.from(applicationContext).notify(
            System.currentTimeMillis().toInt(),
            notification
        )
    }
}
