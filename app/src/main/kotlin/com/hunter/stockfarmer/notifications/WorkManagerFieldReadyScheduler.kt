package com.hunter.stockfarmer.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration

/** WorkManager persists the reminder across process death and reboot; its initial delay is a minimum. */
class WorkManagerFieldReadyScheduler(
    context: Context,
) : FieldReadyScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    init {
        FieldReadyWorker.createChannel(context.applicationContext)
    }

    override fun replace(delay: Duration) {
        val request = OneTimeWorkRequestBuilder<FieldReadyWorker>().setInitialDelay(delay).build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "fieldReady"
    }
}
