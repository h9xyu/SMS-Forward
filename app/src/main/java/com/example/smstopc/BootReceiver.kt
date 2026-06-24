package com.example.smstopc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!AppPreferences.isEnabled) return

        val serviceIntent = Intent(context, ForwardService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)

        schedulePeriodicSweep(context)

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val db = ForwardDatabase.getInstance()
                val pendingRecords = db.forwardDao().getPending()

                if (pendingRecords.isNotEmpty()) {
                    val workRequest = OneTimeWorkRequestBuilder<ForwardWorker>()
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                        .addTag("sms_forward_boot_retry")
                        .build()

                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "forward_boot_retry",
                        ExistingWorkPolicy.REPLACE,
                        workRequest
                    )
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private fun schedulePeriodicSweep(context: Context) {
        val periodicWork = PeriodicWorkRequestBuilder<ForwardWorker>(
            15, TimeUnit.MINUTES
        )
            .addTag("sms_forward_periodic")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sms_forward_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
    }
}
