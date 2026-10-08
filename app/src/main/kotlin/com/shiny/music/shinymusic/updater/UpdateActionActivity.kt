package com.shiny.music.shinymusic.updater

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

/**
 * What an update notification opens. The notification's PendingIntent names this activity, and
 * the browser or the package installer is only chosen here, by the app itself: a PendingIntent
 * that carried the open-ended VIEW intent would let whoever receives it decide where it goes.
 */
class UpdateActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        val apkPath = intent.getStringExtra(EXTRA_APK_PATH)
        val target = when {
            url != null -> Intent(Intent.ACTION_VIEW, url.toUri())
            apkPath != null -> Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    FileProvider.getUriForFile(this@UpdateActionActivity, "$packageName.FileProvider", File(apkPath)),
                    "application/vnd.android.package-archive"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            else -> null
        }
        target?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { target?.let(::startActivity) }
        finish()
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_APK_PATH = "apkPath"

        fun openUrl(context: Context, url: String): Intent =
            Intent(context, UpdateActionActivity::class.java).putExtra(EXTRA_URL, url)

        fun installApk(context: Context, filePath: String): Intent =
            Intent(context, UpdateActionActivity::class.java).putExtra(EXTRA_APK_PATH, filePath)
    }
}
