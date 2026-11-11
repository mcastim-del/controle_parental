package br.com.controleparental

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Locale

/** Paleta calma usada nas telas da criança (sem vermelho, sem cores fortes). */
object Palette {
    val BACKGROUND = Color.rgb(250, 249, 253)
    val TEXT = Color.rgb(38, 33, 92)
    val TEXT_SOFT = Color.rgb(95, 94, 90)
    val MUTED = Color.rgb(180, 178, 169)

    val TEAL_BG = Color.rgb(225, 245, 238)
    val TEAL_RING = Color.rgb(159, 225, 203)
    val TEAL_TEXT = Color.rgb(8, 80, 65)

    val PURPLE_BG = Color.rgb(238, 237, 254)
    val PURPLE_TEXT = Color.rgb(60, 52, 137)

    val AMBER_BG = Color.rgb(250, 238, 218)
    val AMBER_BAR = Color.rgb(239, 159, 39)
    val AMBER_TEXT = Color.rgb(99, 56, 6)

    val GRAY_BG = Color.rgb(241, 239, 232)
}

/** Funções de apoio para montar telas por código. */
class UiKit(private val context: Context) {

    fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null, strokeDp: Int = 0) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(strokeDp), strokeColor)
        }

    fun oval(color: Int, strokeColor: Int? = null, strokeDp: Int = 0) =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (strokeColor != null) setStroke(dp(strokeDp), strokeColor)
        }

    fun text(
        ctx: Context, value: String, sizeSp: Float,
        color: Int = Palette.TEXT, bold: Boolean = false,
    ) = TextView(ctx).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    /** Círculo colorido com um emoji grande no centro. */
    fun emojiCircle(ctx: Context, emoji: String, bg: Int, sizeDp: Int, emojiSp: Float) =
        FrameLayout(ctx).apply {
            background = oval(bg)
            addView(
                text(ctx, emoji, emojiSp),
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            layoutParams = android.widget.LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }
}

/** Lê frases em voz alta (português do Brasil) com a voz do próprio Android. */
class Speaker(context: Context) {
    private var ready = false
    private var pending: String? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            pending?.let { speak(it) }
            pending = null
        }
    }

    init {
        runCatching { tts.setLanguage(Locale("pt", "BR")) }
        runCatching { tts.setSpeechRate(0.85f) } // um pouco mais devagar, mais fácil de entender
    }

    fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        runCatching {
            tts.setLanguage(Locale("pt", "BR"))
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cp")
        }
    }

    fun stop() = runCatching { tts.stop() }

    fun shutdown() = runCatching { tts.shutdown() }
}
