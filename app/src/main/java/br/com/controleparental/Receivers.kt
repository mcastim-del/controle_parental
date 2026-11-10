package br.com.controleparental

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reinicia a contagem quando o celular liga ou o app é atualizado. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs(context).hasPin()) ScreenTimeService.start(context)
    }
}

/**
 * Administrador do dispositivo: impede desinstalar/forçar parada do app sem
 * antes desativar a administração, e permite travar a tela como alternativa.
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "Desativar isto remove a proteção do Controle Parental. Somente o responsável deve fazer isso."
}
