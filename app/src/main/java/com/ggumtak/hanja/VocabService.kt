package com.ggumtak.hanja

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Adds words to Anki (VocabAdder) in a foreground service, so the lookup card can be closed and the user can do
 * something else while Claude writes the note: Android does not stop the work when the app leaves the screen.
 * The result comes back as a toast, wherever the user is.
 */
class VocabService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var running = 0
    private var generation = 0
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val target = intent?.getStringExtra(TARGET)
        // Every start must reach the foreground, or Android ends the app.
        foreground(VocabNote.key(intent?.getStringExtra(FORM).orEmpty(), target.orEmpty()))
        if (target == null) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        val form = intent.getStringExtra(FORM).orEmpty()
        val reading = intent.getStringExtra(READING).orEmpty()
        val sentence = intent.getStringExtra(SENTENCE).orEmpty()
        val prefix = intent.getStringExtra(PREFIX).orEmpty()
        val quiet = intent.getBooleanExtra(QUIET_FAILURE, false)
        val gen = generation
        running++
        val app = applicationContext
        Thread {
            val o = VocabAdder.add(app, target, form, reading, sentence, auto = quiet)
            main.post {
                // An open card shows a success on its button: no toast then. Otherwise one short toast.
                val key = VocabNote.key(form, target)
                var shown = false
                for (l in listeners) if (l(key, o)) shown = true
                if (!(shown && (o.added || o.already)) && (o.added || !quiet)) {
                    Toast.makeText(app, prefix + o.message, Toast.LENGTH_SHORT).show()
                }
                if (gen == generation && running > 0) {
                    running--
                    stopIfIdle()
                }
            }
        }.start()
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        generation++
        running = 0
        VocabAdder.cancelActive()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        onTimeout(startId)
    }

    private fun stopIfIdle() {
        if (running > 0) return
        if (stopSelfResult(lastStartId)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun foreground(word: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "단어장 추가", NotificationManager.IMPORTANCE_LOW))
        }
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("단어장에 추가 중")
            .setContentText(if (word.isEmpty()) "Anki 카드를 생성하는 중…" else "'$word' 카드 생성 중…")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION, n)
        }
    }

    companion object {
        private const val CHANNEL = "vocab"
        private const val NOTIFICATION = 51
        private const val TARGET = "target"
        private const val FORM = "form"
        private const val READING = "reading"
        private const val SENTENCE = "sentence"
        private const val PREFIX = "prefix"
        private const val QUIET_FAILURE = "quiet"

        /** Open lookup cards, told when a word is done (main thread): word key and outcome; true when any showed it. */
        val listeners = CopyOnWriteArraySet<(String, VocabAdder.Outcome) -> Boolean>()

        /**
         * Starts adding the word; false when Android refused to start the service (the app not on screen any
         * more), and the caller then adds it itself. [prefix] goes before the result message; [quiet]: no toast
         * when it fails (the automatic add).
         */
        fun start(ctx: Context, target: String, form: String, reading: String, sentence: String,
                  prefix: String = "", quiet: Boolean = false): Boolean = try {
            val i = Intent(ctx, VocabService::class.java)
                .putExtra(TARGET, target).putExtra(FORM, form).putExtra(READING, reading)
                .putExtra(SENTENCE, sentence).putExtra(PREFIX, prefix).putExtra(QUIET_FAILURE, quiet)
            ctx.startForegroundService(i)
            true
        } catch (e: Exception) {
            false
        }
    }
}
