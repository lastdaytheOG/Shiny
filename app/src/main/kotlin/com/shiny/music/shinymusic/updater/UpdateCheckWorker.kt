package com.shiny.music.shinymusic.updater

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shiny.music.BuildConfig
import com.shiny.music.shinymusic.UpdateNotificationHelper
import java.io.IOException
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val outcome = deliverUpdateNotification(
            autoCheck = getAutoUpdateCheckSetting(context),
            notificationsEnabled = getUpdateNotificationsSetting(context),
            currentVersion = BuildConfig.VERSION_NAME,
            lastNotified = getLastNotifiedUpdate(context),
            check = { latestAvailableUpdate(context) },
            post = { UpdateNotificationHelper.showUpdateNotification(context, it) },
            remember = { saveLastNotifiedUpdate(context, it) },
        )
        Log.d("UpdateCheck", "Background check: $outcome")
        return Result.success()
    }
}

object UpdateCheckScheduler {
    private const val WORK_NAME = "shiny_update_check"

    fun sync(context: Context) {
        val workManager = WorkManager.getInstance(context)
        if (!getAutoUpdateCheckSetting(context)) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

internal enum class UpdateDelivery { Disabled, UpToDate, AlreadyNotified, Posted, NotPosted, CheckFailed }

internal fun shouldNotifyAboutUpdate(latest: String, currentVersion: String, lastNotified: String?): Boolean =
    isNewerVersion(latest, currentVersion) && (lastNotified == null || isNewerVersion(latest, lastNotified))

internal suspend fun deliverUpdateNotification(
    autoCheck: Boolean,
    notificationsEnabled: Boolean,
    currentVersion: String,
    lastNotified: String?,
    check: suspend () -> String?,
    post: (String) -> Boolean,
    remember: (String) -> Unit,
): UpdateDelivery {
    if (!autoCheck || !notificationsEnabled) return UpdateDelivery.Disabled
    val latest = try {
        check()
    } catch (e: IOException) {
        return UpdateDelivery.CheckFailed
    } ?: return UpdateDelivery.UpToDate
    if (!isNewerVersion(latest, currentVersion)) return UpdateDelivery.UpToDate
    if (!shouldNotifyAboutUpdate(latest, currentVersion, lastNotified)) return UpdateDelivery.AlreadyNotified
    if (!post(latest)) return UpdateDelivery.NotPosted
    remember(latest)
    return UpdateDelivery.Posted
}

private suspend fun latestAvailableUpdate(context: Context): String? {
    var latest: String? = null
    var failed = false
    checkForUpdate(
        context = context,
        onSuccess = { tag, isAvailable, _, _, _, _, _, _ ->
            saveUpdateAvailableState(context, isAvailable)
            if (isAvailable) latest = tag
        },
        onError = { failed = true },
    )
    if (failed) throw IOException("Update check failed")
    return latest
}

private const val KEY_LAST_NOTIFIED_UPDATE = "last_notified_update"

fun getLastNotifiedUpdate(context: Context): String? =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LAST_NOTIFIED_UPDATE, null)

fun saveLastNotifiedUpdate(context: Context, version: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_LAST_NOTIFIED_UPDATE, version).apply()
}
