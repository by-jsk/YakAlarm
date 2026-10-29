package com.yakalarm.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.Locale
import kotlin.math.max

/**
 * 약 이름을 음성으로 외치는 알람.
 * - 울리기 시작하면 약 이름을 10번 외침
 * - 끄기 전까지 1분마다 다시 10번씩 외침
 */
class AlarmService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val EXTRA_NAME = "name"
        const val ACTION_STOP = "com.yakalarm.app.STOP"
        const val ACTION_CLOSED = "com.yakalarm.app.CLOSED"
        const val ACTION_STARTED = "com.yakalarm.app.STARTED"
        const val CHANNEL_ID = "med_alarm"
        private const val NOTIF_ID = 1001
        private const val REPEAT_COUNT = 10           // 한 번에 외치는 횟수
        private const val ROUND_INTERVAL_MS = 60_000L // 1분마다 반복
        private const val GAP_MS = 700L               // 외침 사이 쉬는 시간
        private const val LAST_ID = "last"

        /** 지금 울리고 있는 약 이름들 (알람 화면에서 표시) */
        @Volatile
        var activeNames: List<String> = emptyList()

        fun createChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val ch = NotificationChannel(CHANNEL_ID, "복약 알람", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "약 먹을 시간 알림"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val names = LinkedHashSet<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var roundStart = 0L
    private var speaking = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var vibrator: Vibrator? = null
    private var ringtone: Ringtone? = null
    private val roundRunnable = Runnable { startRound() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel(this)

        if (intent?.action == ACTION_STOP) {
            stopAlarm()
            return START_NOT_STICKY
        }

        intent?.getStringExtra(EXTRA_NAME)?.let { names.add(it) }
        activeNames = names.toList()
        startForegroundCompat()
        sendBroadcast(Intent(ACTION_STARTED).setPackage(packageName))

        if (names.isEmpty()) {
            stopAlarm()
            return START_NOT_STICKY
        }

        acquireWakeLock()
        startVibration()

        if (tts == null) {
            tts = TextToSpeech(this, this)
        } else if (ttsReady && !speaking) {
            // 기다리는 중에 다른 약 알람이 추가되면 바로 다시 외침
            handler.removeCallbacks(roundRunnable)
            startRound()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun buildNotification(): Notification {
        val text = if (names.isEmpty()) "약 알람" else names.joinToString(", ")
        val fullIntent = Intent(this, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val fullPi = PendingIntent.getActivity(
            this, 0, fullIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("약 드실 시간이에요")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullPi, true)
            .setContentIntent(fullPi)
            .addAction(0, "끄기", stopPi)
            .build()
    }

    override fun onInit(status: Int) {
        val t = tts
        if (status != TextToSpeech.SUCCESS || t == null) {
            // 음성 엔진이 없으면 기본 알람음으로 대체
            startRingtoneFallback()
            return
        }
        val r = t.setLanguage(Locale.KOREAN)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            t.setLanguage(Locale.getDefault())
        }
        t.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        t.setSpeechRate(0.9f)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (utteranceId == LAST_ID) handler.post { onRoundFinished() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == LAST_ID) handler.post { onRoundFinished() }
            }
        })
        ttsReady = true
        if (names.isNotEmpty()) startRound()
    }

    /** 약 이름을 10번 외치기 */
    private fun startRound() {
        val t = tts ?: return
        if (!ttsReady || names.isEmpty()) return
        acquireWakeLock()
        speaking = true
        roundStart = SystemClock.elapsedRealtime()
        val text = names.joinToString(", ")
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        for (i in 1..REPEAT_COUNT) {
            val mode = if (i == 1) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            t.speak(text, mode, params, if (i == REPEAT_COUNT) LAST_ID else "say$i")
            if (i < REPEAT_COUNT) t.playSilentUtterance(GAP_MS, TextToSpeech.QUEUE_ADD, "gap$i")
        }
    }

    /** 10번 다 외쳤으면 1분 주기에 맞춰 다음 회차 예약 */
    private fun onRoundFinished() {
        if (!speaking) return
        speaking = false
        if (names.isEmpty()) return
        val elapsed = SystemClock.elapsedRealtime() - roundStart
        handler.removeCallbacks(roundRunnable)
        handler.postDelayed(roundRunnable, max(0L, ROUND_INTERVAL_MS - elapsed))
    }

    private fun acquireWakeLock() {
        val wl = wakeLock ?: (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YakAlarm::alarm")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        wl.acquire(10 * 60 * 1000L)
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        if (vibrator != null) return
        val v: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!v.hasVibrator()) return
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0), attrs)
        vibrator = v
    }

    private fun startRingtoneFallback() {
        if (ringtone != null) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            play()
        }
    }

    private fun cleanup() {
        names.clear()
        activeNames = emptyList()
        handler.removeCallbacks(roundRunnable)
        speaking = false
        tts?.stop()
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun stopAlarm() {
        cleanup()
        sendBroadcast(Intent(ACTION_CLOSED).setPackage(packageName))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        cleanup()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
