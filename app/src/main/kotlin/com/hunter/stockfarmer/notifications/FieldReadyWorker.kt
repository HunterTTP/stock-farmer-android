package com.hunter.stockfarmer.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters

class FieldReadyWorker(
    context: Context,
    parameters: WorkerParameters,
) : Worker(context, parameters) {
    override fun doWork(): Result {
        val context = applicationContext
        val manager = context.getSystemService(NotificationManager::class.java)
        if (isStopped || !manager.areNotificationsEnabled() || !hasPermission(context)) return Result.success()
        createChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val notification =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle("Stock Farmer")
                .setContentText("All your crops are ready to harvest!")
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            notification.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        if (!isStopped) {
            try {
                manager.notify(NOTIFICATION_ID, notification.build())
            } catch (_: SecurityException) {
                // Authorization may be revoked between the check and delivery; don't retry a stale reminder.
            }
        }
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "fieldReady"
        private const val NOTIFICATION_ID = 1

        internal fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL_ID, "Harvest reminders", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "When all your crops are ready to harvest"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun hasPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}
