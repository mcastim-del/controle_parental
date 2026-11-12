package br.com.controleparental

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/**
 * "Vigia" por alarme do Android.
 *
 * Alguns celulares (ex.: Motorola) congelam ou encerram o serviço de contagem
 * em segundo plano. Este alarme acorda o app a cada minuto e chama o serviço:
 * se ele foi encerrado, é iniciado de novo; se estava congelado, volta a contar.
 */
class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs(context).hasPin()) return
        if (!ScreenTimeService.running) Diag.log(context, "Vigia: contagem parada, reiniciando")
        runCatching { ScreenTimeService.start(context) }
            .onFailure { Diag.log(context, "Vigia: falha ao reiniciar (${it.javaClass.simpleName})") }
        schedule(context, INTERVAL_MS)
    }

    companion object {
        private const val INTERVAL_MS = 60_000L

        /** Agenda a próxima verificação (substitui a anterior). */
        fun schedule(context: Context, delayMs: Long = INTERVAL_MS) {
            val am = context.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(context, WatchdogReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val at = SystemClock.elapsedRealtime() + delayMs
            val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            runCatching {
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            }.onFailure {
                runCatching { am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi) }
            }
        }
    }
}
