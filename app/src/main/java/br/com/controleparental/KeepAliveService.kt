package br.com.controleparental

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * "Vigia" do Controle Parental.
 *
 * Alguns celulares (ex.: Motorola) congelam e encerram o serviço de contagem
 * quando o app vai para segundo plano. Um serviço de Acessibilidade fica ligado
 * pelo próprio sistema e praticamente nunca é encerrado; ele verifica a cada
 * poucos segundos se a contagem está rodando e, se não estiver, inicia de novo.
 *
 * Não lê nem guarda nada do que aparece na tela.
 */
class KeepAliveService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastCheck = 0L

    private val watchdog = object : Runnable {
        override fun run() {
            ensureRunning()
            handler.postDelayed(this, CHECK_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Diag.log(this, "Vigia (Acessibilidade) LIGADO")
        handler.removeCallbacks(watchdog)
        handler.post(watchdog)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Troca de tela/app: aproveita para conferir (no máximo a cada 2 s).
        val now = System.currentTimeMillis()
        if (now - lastCheck >= 2_000) ensureRunning()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        Diag.log(this, "Vigia (Acessibilidade) DESLIGADO")
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun ensureRunning() {
        lastCheck = System.currentTimeMillis()
        if (ScreenTimeService.running) return
        if (!Prefs(this).hasPin()) return
        Diag.log(this, "Vigia: contagem parada, reiniciando")
        runCatching { ScreenTimeService.start(this) }
            .onFailure { Diag.log(this, "Vigia: falha ao reiniciar (${it.javaClass.simpleName})") }
    }

    companion object {
        private const val CHECK_MS = 5_000L

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, KeepAliveService::class.java).flattenToString()
            val list = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return list.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
