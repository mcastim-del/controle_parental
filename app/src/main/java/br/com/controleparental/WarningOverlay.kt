package br.com.controleparental

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * Aviso de transição mostrado quando faltam 5 e 1 minuto:
 * "Faltam 5 minutos" + barra que diminui + sequência "Agora: celular → Depois: brincar".
 * Some sozinho depois de 10 segundos ou com um toque.
 */
class WarningOverlay(private val context: Context, private val speaker: Speaker) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private val ui = UiKit(context)
    private val themed = ContextThemeWrapper(context, android.R.style.Theme_Material_Light_NoActionBar)
    private val handler = Handler(Looper.getMainLooper())
    private var root: View? = null

    /** @param minutesLeft minutos restantes; @param fraction quanto da barra ainda está cheia (0..1). */
    fun show(minutesLeft: Int, fraction: Float) {
        hide()
        if (!Settings.canDrawOverlays(context)) return

        val title = if (minutesLeft <= 1) "Falta 1 minuto" else "Faltam $minutesLeft minutos"

        val col = LinearLayout(themed).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(ui.dp(32), ui.dp(32), ui.dp(32), ui.dp(32))
        }
        col.addView(ui.emojiCircle(themed, "⏳", Palette.AMBER_BG, 110, 56f))
        col.addView(ui.text(themed, title, 30f, bold = true), wrap(20))

        // Barra que mostra quanto tempo ainda resta.
        val bar = FrameLayout(themed).apply { background = ui.rounded(Palette.GRAY_BG, 12) }
        val fill = View(themed).apply { background = ui.rounded(Palette.AMBER_BAR, 12) }
        bar.addView(fill, FrameLayout.LayoutParams(0, MATCH_PARENT))
        col.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, ui.dp(24)).apply { topMargin = ui.dp(20) })
        bar.post {
            fill.layoutParams = fill.layoutParams.apply {
                width = (bar.width * fraction.coerceIn(0.04f, 1f)).toInt()
            }
        }

        col.addView(
            ui.text(themed, "Quando a barra acabar, o celular vai dormir.", 18f, Palette.TEXT_SOFT),
            wrap(16)
        )

        val steps = LinearLayout(themed).apply { orientation = LinearLayout.HORIZONTAL }
        steps.addView(step("📱", "Agora\ncelular", Palette.PURPLE_BG, Palette.PURPLE_TEXT))
        steps.addView(ui.text(themed, "→", 22f, Palette.MUTED).apply {
            setPadding(ui.dp(8), 0, ui.dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        })
        steps.addView(step("🧩", "Depois\nbrincar", Palette.TEAL_BG, Palette.TEAL_TEXT))
        col.addView(steps, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = ui.dp(24) })

        col.addView(ui.text(themed, "Toque para continuar", 14f, Palette.MUTED), wrap(28))

        val frame = FrameLayout(themed).apply {
            setBackgroundColor(Palette.BACKGROUND)
            addView(col, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
            setOnClickListener { hide() }
        }

        val params = WindowManager.LayoutParams(
            MATCH_PARENT, MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        )
        try {
            wm.addView(frame, params)
            root = frame
            speaker.speak("$title. Quando a barra acabar, o celular vai dormir.")
            handler.postDelayed({ hide() }, 10_000)
        } catch (_: Exception) {
        }
    }

    fun hide() {
        handler.removeCallbacksAndMessages(null)
        root?.let { runCatching { wm.removeView(it) } }
        root = null
    }

    private fun wrap(topDp: Int) = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
        topMargin = ui.dp(topDp)
        gravity = Gravity.CENTER_HORIZONTAL
    }

    private fun step(emoji: String, label: String, bg: Int, fg: Int) = LinearLayout(themed).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = ui.rounded(bg, 14)
        setPadding(0, ui.dp(12), 0, ui.dp(12))
        addView(ui.text(themed, emoji, 30f))
        addView(ui.text(themed, label, 15f, fg, bold = true))
        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
    }
}
