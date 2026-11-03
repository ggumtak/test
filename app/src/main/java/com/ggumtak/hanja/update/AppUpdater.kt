package com.ggumtak.hanja.update

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.ggumtak.hanja.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app update from the GitHub "latest" release (the user, 2026-11-03: update inside the app like their other
 * app). [check] asks GitHub for the newest Hanja-N.apk; [install] downloads it to cache/update and hands it to
 * [PackageInstaller], which shows Android's confirm screen. Android only accepts an APK signed like the installed
 * app, which every CI build is. Network and file work run on a background thread; callbacks on the main thread.
 */
object AppUpdater {
    private const val DIR = "update"
    private const val MAX_BYTES = 100L * 1024 * 1024
    const val ACTION_STATUS = "com.ggumtak.hanja.UPDATE_STATUS"

    private val main = Handler(Looper.getMainLooper())

    val current: Int get() = BuildConfig.VERSION_CODE

    /** The newest release, or null when GitHub could not be asked. */
    fun check(done: (UpdateInfo.Latest?) -> Unit) {
        Thread {
            val latest = runCatching { getText(UpdateInfo.LATEST_URL)?.let(UpdateInfo::parse) }.getOrNull()
            main.post { done(latest) }
        }.start()
    }

    /**
     * Downloads [latest] and starts the install. [progress] gets percentages (-1: size unknown); [failed] a
     * message. Without the "install unknown apps" permission it opens that setting instead and returns false.
     */
    fun install(activity: Activity, latest: UpdateInfo.Latest, progress: (Int) -> Unit, failed: (String) -> Unit): Boolean {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            runCatching {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName)))
            }
            return false
        }
        val app = activity.applicationContext
        Thread {
            val file = runCatching { fetchApk(app, latest) { p -> main.post { progress(p) } } }.getOrNull()
            if (file == null) {
                main.post { failed("업데이트를 내려받지 못했습니다") }
                return@Thread
            }
            val ok = runCatching { commit(app, file) }.isSuccess
            if (!ok) {
                file.delete()
                main.post { failed("업데이트를 설치하지 못했습니다") }
            }
        }.start()
        return true
    }

    fun forgetDownloads(context: Context) {
        File(context.cacheDir, DIR).listFiles()?.forEach { it.delete() }
    }

    private fun commit(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out, 64 * 1024) }
                    session.fsync(out)
                }
                val intent = Intent(context, UpdateReceiver::class.java).setAction(ACTION_STATUS)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
            }
        } catch (t: Throwable) {
            runCatching { installer.abandonSession(id) }
            throw t
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/vnd.github+json, */*")
        setRequestProperty("User-Agent", "hanja-app")
    }

    private fun getText(url: String): String? {
        val c = open(url)
        try {
            if (c.responseCode != 200) return null
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** The APK in cache/update: a finished earlier download is reused, anything else is fetched again. */
    private fun fetchApk(context: Context, latest: UpdateInfo.Latest, progress: (Int) -> Unit): File? {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.filter { it.name != UpdateInfo.fileName(latest.build) }?.forEach { it.delete() }
        val target = File(dir, UpdateInfo.fileName(latest.build))
        if (latest.size > 0 && target.length() == latest.size) return target
        val part = File(dir, target.name + ".part")
        val c = open(latest.url)
        try {
            if (c.responseCode != 200) return null
            val total = c.contentLengthLong.takeIf { it > 0 } ?: latest.size
            if (total > MAX_BYTES) return null
            var done = 0L
            var shown = -2
            c.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done > MAX_BYTES) return null
                        val p = if (total > 0) (done * 100 / total).toInt() else -1
                        if (p != shown) {
                            shown = p
                            progress(p)
                        }
                    }
                }
            }
            if (total > 0 && done != total) return null
            if (!part.renameTo(target)) return null
            return target
        } finally {
            c.disconnect()
        }
    }
}
