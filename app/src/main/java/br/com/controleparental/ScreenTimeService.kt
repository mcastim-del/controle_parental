package br.com.controleparental

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock

/**
 * Serviço em primeiro plano que conta o tempo com a tela ligada e,
 * quando o limite diário acaba, cobre a tela com o bloqueio (LockOverlay).
 */
class ScreenTimeService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var overlay: LockOverlay
    private lateinit var speaker: Speaker
    private lateinit var sound: SoundBlocker
    private val handler = Handler(Looper.getMainLooper())

    /** elapsedRealtime da última contagem; 0 = tela desligada (sem contar). */
    private var lastTick = 0L
    /** Momento em que o usuário desbloqueou a tela (para dar tempo ao responsável abrir o app). */
    private var userPresentAt = 0L
    private var lastNotificationText = ""
    /** Próxima contagem: a cada 5 s, ou antes se o tempo acabar antes disso (bloqueio na hora certa). */
    private var nextTickMs = TICK_MS

    private val tick = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, nextTickMs)
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> startCounting()
                Intent.ACTION_SCREEN_OFF -> stopCounting()
                Intent.ACTION_USER_PRESENT -> {
                    userPresentAt = SystemClock.elapsedRealtime()
                    update()
                }
                else -> update() // data/hora/fuso alterados
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        speaker = Speaker(this)
        sound = SoundBlocker(this)
        overlay = LockOverlay(this, prefs, speaker) {
            previewing = false
            update()
        }
        createChannels()

        val notification = buildNotification("Iniciando…")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(systemReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(systemReceiver, filter)
        }

        userPresentAt = SystemClock.elapsedRealtime()
        if (getSystemService(PowerManager::class.java).isInteractive) startCounting() else update()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PREVIEW) previewing = true
        update()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(systemReceiver) }
        overlay.hide()
        sound.release()
        speaker.shutdown()
        super.onDestroy()
    }

    /** Tela ligada: verifica a cada poucos segundos se o celular está em uso. */
    private fun startCounting() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    /** Tela desligada (stand by): fecha a conta até agora e para. */
    private fun stopCounting() {
        update()
        lastTick = 0L
        handler.removeCallbacks(tick)
    }

    /**
     * Conta enquanto a tela está ligada e o celular está desbloqueado,
     * mesmo que ninguém esteja tocando nela. Para quando a tela apaga
     * ou quando a tela de bloqueio do próprio celular (senha/digital) está aparecendo.
     */
    private fun isScreenInUse(): Boolean {
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        return interactive && !locked
    }

    private fun update() {
        prefs.ensureToday()

        // Acumula o tempo de tela em uso desde a última contagem.
        val now = SystemClock.elapsedRealtime()
        if (lastTick != 0L) {
            val secs = (now - lastTick) / 1000
            if (secs > 0) {
                lastTick += secs * 1000
                // Com o bloqueio na tela, o tempo não é somado ao uso do dia.
                if (prefs.enabled && !prefs.unlimitedToday && !overlay.isShowing) prefs.usedSeconds += secs
            }
        }
        // Começa/continua a contar só com a tela ligada e o celular desbloqueado.
        if (isScreenInUse()) {
            if (lastTick == 0L) lastTick = now
        } else {
            lastTick = 0L
        }

        val remaining = prefs.remainingSeconds()
        val blocked = remaining <= 0 || previewing
        nextTickMs = if (remaining in 1L until TICK_MS / 1000) remaining * 1000 else TICK_MS

        if (blocked) {
            sound.block()
            val shown = overlay.show()
            // Sem permissão de sobreposição (ex.: foi revogada), usa o bloqueio
            // de tela do administrador do dispositivo como alternativa.
            if (!shown && lastTick != 0L) lockWithDeviceAdmin()
        } else {
            overlay.hide()
            sound.release()
        }
        updateNotification(remaining, blocked)
    }

    private fun lockWithDeviceAdmin() {
        if (MainActivity.parentSessionActive) return
        // Dá alguns segundos após desbloquear para o responsável abrir o app.
        if (SystemClock.elapsedRealtime() - userPresentAt < ADMIN_GRACE_MS) return
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(this, AdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) runCatching { dpm.lockNow() }
    }

    private fun updateNotification(remaining: Long, blocked: Boolean) {
        val text = when {
            !prefs.enabled -> "Controle desativado"
            prefs.unlimitedToday -> "Sem limite hoje (liberado pelo responsável)"
            blocked -> "Tempo esgotado • usado ${formatDuration(prefs.usedSeconds)}"
            else -> "Restam ${formatDuration(remaining)} • usado ${formatDuration(prefs.usedSeconds)}"
        }
        if (text == lastNotificationText) return
        lastNotificationText = text
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Tempo de tela")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Tempo de tela restante", NotificationManager.IMPORTANCE_LOW)
        )
        // Sem avisos antes do fim do tempo: o bloqueio acontece direto.
        runCatching { nm.deleteNotificationChannel("screen_time_alerts") }
    }

    companion object {
        private const val CHANNEL_ID = "screen_time"
        private const val NOTIF_ID = 1
        private const val TICK_MS = 5_000L
        private const val ADMIN_GRACE_MS = 20_000L

        /** Inicia o serviço (ou pede para ele reavaliar o estado, se já estiver rodando). */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, ScreenTimeService::class.java))
        }

        private const val ACTION_PREVIEW = "br.com.controleparental.PREVIEW"

        /** true quando o responsável pediu para ver a tela de bloqueio como teste. */
        @Volatile
        var previewing = false

        /** Mostra a tela de bloqueio agora, como teste (sai com o PIN). */
        fun preview(context: Context) {
            context.startForegroundService(
                Intent(context, ScreenTimeService::class.java).setAction(ACTION_PREVIEW)
            )
        }
    }
}
