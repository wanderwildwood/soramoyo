package com.wanderwildwood.soramoyo.alerts.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Reconnects the built-in distributor after a failure, after a growing delay. The
 * quarter-hourly alert check does the rest of the looking after ([EmbeddedPush.keepAlive]).
 *
 * After Sunup's RestartWorker (https://codeberg.org/Sunup/android, Apache-2.0, commit
 * fe768d0 "1.3.3"), by way of Whereabouts. WorkManager runs it inside Doze's maintenance
 * windows, and only with a network.
 */
class PushRestartWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        EmbeddedPush.keepAlive(applicationContext)
        return Result.success()
    }

    companion object {
        private const val ONCE = "push-reconnect"

        fun runOnce(context: Context, delayMillis: Long) {
            val work = OneTimeWorkRequestBuilder<PushRestartWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, work)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(ONCE)
        }
    }
}
