package br.com.controleparental

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Silencia vídeos e músicas enquanto a tela de bloqueio está aberta:
 *  1. pede o "foco de áudio" do Android — YouTube, Netflix, Spotify etc. pausam sozinhos;
 *  2. muta o volume de mídia, para apps que ignoram o foco (alguns jogos);
 *  3. ao liberar, devolve o som como estava.
 */
class SoundBlocker(context: Context) {

    private val am = context.getSystemService(AudioManager::class.java)
    private val prefs = context.applicationContext
        .getSharedPreferences("controle_parental_som", Context.MODE_PRIVATE)
    private var active = false

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener { change ->
            // Se outro app tomar o som de volta durante o bloqueio, pede de novo.
            if (active && change == AudioManager.AUDIOFOCUS_LOSS) requestFocus()
        }
        .build()

    /** Chamado a cada contagem enquanto o bloqueio está na tela. */
    fun block() {
        if (!active) {
            active = true
            requestFocus()
        }
        // Mantém o volume de mídia mutado (mesmo que a criança aperte o botão de volume).
        if (!am.isStreamMute(AudioManager.STREAM_MUSIC)) {
            runCatching { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0) }
            prefs.edit().putBoolean(K_MUTED_BY_US, true).apply()
        }
    }

    /** Devolve o som quando o bloqueio sai. Também corrige um mudo esquecido (ex.: app reiniciado). */
    fun release() {
        if (active) {
            active = false
            runCatching { am.abandonAudioFocusRequest(focusRequest) }
        }
        if (prefs.getBoolean(K_MUTED_BY_US, false)) {
            runCatching { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0) }
            prefs.edit().putBoolean(K_MUTED_BY_US, false).apply()
        }
    }

    private fun requestFocus() {
        runCatching { am.requestAudioFocus(focusRequest) }
    }

    companion object {
        private const val K_MUTED_BY_US = "muted_by_us"
    }
}
