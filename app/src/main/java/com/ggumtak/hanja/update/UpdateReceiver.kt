package com.ggumtak.hanja.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/** The installer's answer to [AppUpdater]'s session: shows its confirm screen, or says why it failed. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirm == null || runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isFailure) {
                    AppUpdater.onInstallerFinished("설치 화면을 열지 못했습니다.")
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                AppUpdater.forgetDownloads(context)
                AppUpdater.onInstallerFinished(null)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                AppUpdater.onInstallerFinished(null)
            }
            else -> {
                // A broken download fails every time: the next try downloads again.
                val showFallback = AppUpdater.listener == null
                AppUpdater.forgetDownloads(context)
                AppUpdater.onInstallerFinished("업데이트 설치에 실패했습니다.")
                if (showFallback) Toast.makeText(context.applicationContext, "업데이트 설치에 실패했습니다.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
