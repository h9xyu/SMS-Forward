package com.example.smstopc

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class App : Application() {

    companion object {
        lateinit var context: App
            private set

        const val CHANNEL_ID = "sms_forward_channel"
        const val CHANNEL_NAME = "短信转发服务"
    }

    override fun onCreate() {
        super.onCreate()
        context = this
        createNotificationChannel()
        schedulePeriodicSweep()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示短信转发服务状态"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun schedulePeriodicSweep() {
        val periodicWork = PeriodicWorkRequestBuilder<ForwardWorker>(
            15, TimeUnit.MINUTES
        )
            .addTag("sms_forward_periodic")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "sms_forward_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
    }
}
