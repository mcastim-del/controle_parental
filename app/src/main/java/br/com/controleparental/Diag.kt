package br.com.controleparental

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de diagnóstico: guarda as últimas linhas do que o app fez
 * (tela ligou/desligou, tempo contado, bloqueio mostrado...).
 * Aparece na área do responsável, no botão "Ver diagnóstico".
 */
object Diag {
    private const val FILE = "controle_parental_diag"
    private const val KEY = "log"
    private const val MAX_LINES = 120
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun log(context: Context, message: String) {
        val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val lines = (sp.getString(KEY, "") ?: "").split('\n').filter { it.isNotEmpty() }.toMutableList()
        lines.add("${fmt.format(Date())} $message")
        while (lines.size > MAX_LINES) lines.removeAt(0)
        sp.edit().putString(KEY, lines.joinToString("\n")).apply()
    }

    /** Linhas mais recentes primeiro. */
    fun read(context: Context): String {
        val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val text = sp.getString(KEY, "") ?: ""
        return if (text.isEmpty()) "(vazio)" else text.split('\n').reversed().joinToString("\n")
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
