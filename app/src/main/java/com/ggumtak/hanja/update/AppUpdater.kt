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
 * app). [check] asks GitHub for the newest Hanja-N.apk; [install] downloads only the change when the release has a
 * patch from the installed build (ApkPatch, 2026-11-25: the APK is 25MB, a code change about 0.1MB), else the whole
 * APK, to cache/update and hands it to
 * [PackageInstaller], which shows Android's confirm screen. Android only accepts an APK signed like the installed
 * app, which every CI build is. Network and file work run on a background thread; callbacks on the main thread.
 */
object AppUpdater {
    private const val DIR = "update"
    private const val MAX_BYTES = 100L * 1024 * 1024
    const val ACTION_STATUS = "com.ggumtak.hanja.UPDATE_STATUS"

    private val main = Handler(Looper.getMainLooper())

    val current: Int get() = BuildConfig.VERSION_CODE

    @Volatile
    var busy: Boolean = false
        private set

    @Volatile
    var lastPercent: Int = -1
        private set

    @Volatile
    var listener: ((percent: Int?, error: String?, done: Boolean) -> Unit)? = null

    internal fun onInstallerFinished(error: String?) {
        busy = false
        lastPercent = -1
        main.post { listener?.invoke(null, error, true) }
    }

    /** The newest release, or null when GitHub could not be asked. */
    fun check(done: (UpdateInfo.Latest?) -> Unit) {
        Thread {
            val latest = runCatching { getText(UpdateInfo.LATEST_URL)?.let(UpdateInfo::parse) }.getOrNull()
            main.post { done(latest) }
        }.start()
    }

    /**
     * Downloads [latest] and starts the install, reporting percentages (-1: size unknown) and completion/errors to
     * [listener]. Without the "install unknown apps" permission it opens that setting instead and returns false.
     */
    fun install(activity: Activity, latest: UpdateInfo.Latest): Boolean {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            runCatching {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.packageName)))
            }
            return false
        }
        if (busy) return true
        busy = true
        lastPercent = -1
        val app = activity.applicationContext
        Thread {
            val report: (Int) -> Unit = { p ->
                lastPercent = p
                main.post { listener?.invoke(p, null, false) }
            }
            val file = runCatching { fromPatch(app, latest, report) }.getOrNull()
                ?: runCatching { fetchApk(app, latest, report) }.getOrNull()
            if (file == null) {
                onInstallerFinished("업데이트 다운로드에 실패했습니다.")
                return@Thread
            }
            val ok = runCatching { commit(app, file) }.isSuccess
            if (!ok) {
                file.delete()
                onInstallerFinished("업데이트 설치에 실패했습니다.")
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

    /**
     * The new APK rebuilt from the installed one and the patch for it; null when there is no patch for this build or
     * the result is not exactly the release APK (then the whole APK is downloaded).
     */
    private fun fromPatch(context: Context, latest: UpdateInfo.Latest, progress: (Int) -> Unit): File? {
        val patch = UpdateInfo.patchFor(latest, current) ?: return null
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val target = File(dir, UpdateInfo.fileName(latest.build))
        if (latest.size > 0 && target.length() == latest.size) return target
        val file = File(dir, UpdateInfo.fileName(latest.build) + ".patch")
        if (!download(patch.url, file, patch.size, progress)) return null
        val built = File(dir, target.name + ".part")
        try {
            val ok = file.inputStream().use { ApkPatch.apply(File(context.applicationInfo.sourceDir), it, built) }
            if (!ok) return null
            target.delete()
            return if (built.renameTo(target)) target else null
        } finally {
            file.delete()
            if (built.exists()) built.delete()
        }
    }

    /** The APK in cache/update: a finished earlier download is reused, anything else is fetched again. */
    private fun fetchApk(context: Context, latest: UpdateInfo.Latest, progress: (Int) -> Unit): File? {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.filter { it.name != UpdateInfo.fileName(latest.build) }?.forEach { it.delete() }
        val target = File(dir, UpdateInfo.fileName(latest.build))
        if (latest.size > 0 && target.length() == latest.size) return target
        return if (download(latest.url, target, latest.size, progress)) target else null
    }

    /** [url] into [target] (through a .part file), with percentages; false on any failure. */
    private fun download(url: String, target: File, size: Long, progress: (Int) -> Unit): Boolean {
        val part = File(target.parentFile, target.name + ".part")
        var ok = false
        val c = open(url)
        try {
            if (c.responseCode != 200) return false
            val total = c.contentLengthLong.takeIf { it > 0 } ?: size
            if (total > MAX_BYTES) return false
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
                        if (done > MAX_BYTES) return false
                        val p = if (total > 0) (done * 100 / total).toInt() else -1
                        if (p != shown) {
                            shown = p
                            progress(p)
                        }
                    }
                }
            }
            if (total > 0 && done != total) return false
            target.delete()
            ok = part.renameTo(target)
            return ok
        } finally {
            c.disconnect()
            if (!ok && part.exists()) part.delete()
        }
    }
}
