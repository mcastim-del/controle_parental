package br.com.controleparental

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Tela "O celular foi dormir", desenhada por cima de todos os apps quando o tempo acaba.
 *
 * Tem três modos:
 *  - criança: sequência visual (Brincar → Dormir → Celular) e cartões de atividades;
 *  - atividade escolhida: figura grande + círculo de respiração;
 *  - responsável: aparece ao segurar o cadeado por 3 segundos; pede o PIN para liberar tempo.
 */
class LockOverlay(
    private val context: Context,
    private val prefs: Prefs,
    private val speaker: Speaker,
    private val onUnlocked: () -> Unit,
) {
    private data class Activity(val emoji: String, val label: String, val title: String, val hint: String)

    private val activities = listOf(
        Activity("🎨", "Desenhar", "Vamos desenhar", "Pegue o papel e os lápis."),
        Activity("⚽", "Brincar", "Vamos brincar", "Escolha um brinquedo."),
        Activity("📖", "Ler", "Vamos ler", "Escolha um livro."),
        Activity("🍎", "Lanche", "Hora do lanche", "Vamos comer algo gostoso."),
        Activity("🎵", "Música", "Vamos cantar", "Escolha uma música para cantar."),
        Activity("🤗", "Abraço", "Hora do abraço", "Dê um abraço em quem você ama."),
    )

    private val wm = context.getSystemService(WindowManager::class.java)
    private val ui = UiKit(context)
    private val themed = ContextThemeWrapper(context, android.R.style.Theme_Material_Light_NoActionBar)
    private val handler = Handler(Looper.getMainLooper())

    private var root: FrameLayout? = null
    private var breathing: ValueAnimator? = null

    val isShowing: Boolean get() = root != null

    /** Mostra o bloqueio. Retorna false se não houver permissão de sobreposição. */
    fun show(): Boolean {
        if (root != null) return true
        if (!Settings.canDrawOverlays(context)) return false

        val frame = FrameLayout(themed).apply { setBackgroundColor(Palette.BACKGROUND) }
        val params = WindowManager.LayoutParams(
            MATCH_PARENT, MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        return try {
            wm.addView(frame, params)
            root = frame
            showChildScreen(speak = true)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun hide() {
        stopBreathing()
        handler.removeCallbacksAndMessages(null)
        root?.let { runCatching { wm.removeView(it) } }
        root = null
    }

    // ------------------------------------------------------------------
    // Tela principal da criança
    // ------------------------------------------------------------------

    private val mainSentence =
        "O celular foi dormir. Ele acorda amanhã de manhã. O que vamos fazer agora?"

    private fun showChildScreen(speak: Boolean) {
        stopBreathing()
        val col = column()

        col.addView(ui.emojiCircle(themed, "🌙", Palette.PURPLE_BG, 110, 56f).withTop(8))
        col.addView(ui.text(themed, "O celular foi dormir", 28f, bold = true).withTop(20))
        col.addView(ui.text(themed, "Ele acorda amanhã de manhã.", 18f, Palette.TEXT_SOFT).withTop(6))

        // Sequência visual: o que acontece até o celular voltar.
        val steps = LinearLayout(themed).apply { orientation = LinearLayout.HORIZONTAL }
        steps.addView(step("🧩", "Brincar", Palette.TEAL_BG, Palette.TEAL_TEXT))
        steps.addView(arrow())
        steps.addView(step("🛏️", "Dormir", Palette.PURPLE_BG, Palette.PURPLE_TEXT))
        steps.addView(arrow())
        steps.addView(step("☀️", "Celular", Palette.AMBER_BG, Palette.AMBER_TEXT))
        col.addView(steps, matchWidth().withTopMargin(24))

        col.addView(ui.text(themed, "O que vamos fazer agora?", 20f, bold = true).withTop(28))

        // Cartões de atividades (3 colunas x 2 linhas).
        val grid = GridLayout(themed).apply { columnCount = 3 }
        activities.forEach { act ->
            val card = LinearLayout(themed).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = ui.rounded(Palette.TEAL_BG, 16)
                setPadding(0, ui.dp(14), 0, ui.dp(14))
                addView(ui.text(themed, act.emoji, 36f))
                addView(ui.text(themed, act.label, 15f, Palette.TEAL_TEXT, bold = true).withTop(4))
                setOnClickListener { showActivityScreen(act) }
            }
            grid.addView(card, GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply {
                width = 0
                setMargins(ui.dp(5), ui.dp(5), ui.dp(5), ui.dp(5))
            })
        }
        col.addView(grid, matchWidth().withTopMargin(12))

        col.addView(listenButton { speaker.speak(mainSentence) }.withTop(20))

        setContent(col, withLock = true)
        if (speak) speaker.speak(mainSentence)
    }

    // ------------------------------------------------------------------
    // Depois de escolher uma atividade
    // ------------------------------------------------------------------

    private fun showActivityScreen(act: Activity) {
        val col = column()
        col.addView(ui.emojiCircle(themed, act.emoji, Palette.TEAL_BG, 150, 80f).withTop(24))
        col.addView(ui.text(themed, act.title, 30f, bold = true).withTop(24))
        col.addView(ui.text(themed, "Boa escolha. ${act.hint}", 18f, Palette.TEXT_SOFT).withTop(8))

        // Círculo de respiração: cresce (inspira) e diminui (solta) devagar.
        val label = ui.text(themed, "Inspira", 18f, Palette.TEAL_TEXT, bold = true)
        val inner = FrameLayout(themed).apply {
            background = ui.oval(Palette.TEAL_BG, Palette.TEAL_RING, 4)
            addView(label, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
        val holder = FrameLayout(themed).apply {
            addView(inner, FrameLayout.LayoutParams(ui.dp(150), ui.dp(150), Gravity.CENTER))
        }
        col.addView(holder, LinearLayout.LayoutParams(ui.dp(170), ui.dp(170)).withTopMargin(32))
        col.addView(ui.text(themed, "Respire junto com o círculo.", 15f, Palette.TEXT_SOFT).withTop(8))

        col.addView(Button(themed).apply {
            text = "↩  Escolher outra"
            textSize = 16f
            isAllCaps = false
            setOnClickListener { showChildScreen(speak = false) }
        }.withTop(28))

        setContent(col, withLock = true)
        startBreathing(inner, label)
        speaker.speak("${act.title}. Boa escolha. ${act.hint}")
    }

    private fun startBreathing(target: View, label: TextView) {
        stopBreathing()
        breathing = ValueAnimator.ofFloat(0.65f, 1f).apply {
            duration = 4000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val s = it.animatedValue as Float
                target.scaleX = s
                target.scaleY = s
            }
            var inhaling = true
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationRepeat(animation: Animator) {
                    inhaling = !inhaling
                    label.text = if (inhaling) "Inspira" else "Solta"
                }
            })
            start()
        }
    }

    private fun stopBreathing() {
        breathing?.cancel()
        breathing = null
    }

    // ------------------------------------------------------------------
    // Área do responsável (escondida atrás do cadeado)
    // ------------------------------------------------------------------

    private fun showParentScreen() {
        stopBreathing()
        speaker.stop()
        val col = column()
        col.addView(ui.text(themed, "🔒", 40f).withTop(16))
        col.addView(ui.text(themed, "Área do responsável", 24f, bold = true).withTop(8))
        col.addView(
            ui.text(themed, "Usado hoje: ${formatDuration(prefs.usedSeconds)}", 16f, Palette.TEXT_SOFT).withTop(6)
        )

        val pin = EditText(themed).apply {
            hint = "PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(8))
            gravity = Gravity.CENTER
            textSize = 24f
        }
        col.addView(pin, LinearLayout.LayoutParams(ui.dp(220), WRAP_CONTENT).withTopMargin(20))

        val status = ui.text(themed, "", 15f, Palette.AMBER_TEXT).withTop(8)
        col.addView(status)

        fun grant(extraSeconds: Long?) {
            when (val r = prefs.checkPin(pin.text.toString())) {
                is PinCheck.Ok -> {
                    if (extraSeconds == null) prefs.unlimitedToday = true
                    else prefs.grantExtra(extraSeconds)
                    context.getSystemService(InputMethodManager::class.java)
                        .hideSoftInputFromWindow(pin.windowToken, 0)
                    onUnlocked()
                }
                is PinCheck.Wrong -> status.text = "PIN incorreto. Tentativas restantes: ${r.attemptsLeft}"
                is PinCheck.Locked -> status.text = "Muitas tentativas. Aguarde ${r.seconds} segundos."
            }
            pin.setText("")
        }

        val row = LinearLayout(themed).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("+15 min" to 15 * 60L, "+30 min" to 30 * 60L, "+1 hora" to 60 * 60L).forEach { (label, secs) ->
            row.addView(Button(themed).apply {
                text = label
                isAllCaps = false
                setOnClickListener { grant(secs) }
            })
        }
        col.addView(row.withTop(12))
        col.addView(Button(themed).apply {
            text = "Liberar o resto do dia"
            isAllCaps = false
            setOnClickListener { grant(null) }
        })
        col.addView(Button(themed).apply {
            text = "Voltar"
            isAllCaps = false
            setOnClickListener {
                context.getSystemService(InputMethodManager::class.java)
                    .hideSoftInputFromWindow(pin.windowToken, 0)
                showChildScreen(speak = false)
            }
        }.withTop(16))

        setContent(col, withLock = false)
    }

    /** Cadeado pequeno no canto: só abre a área do responsável se for segurado por 3 segundos. */
    @SuppressLint("ClickableViewAccessibility")
    private fun lockButton(): View = ui.text(themed, "🔒", 16f).apply {
        alpha = 0.45f
        setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16))
        val open = Runnable { showParentScreen() }
        setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> handler.postDelayed(open, 3000)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(open)
            }
            true
        }
    }

    // ------------------------------------------------------------------
    // Apoio de layout
    // ------------------------------------------------------------------

    private fun column() = LinearLayout(themed).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(ui.dp(24), ui.dp(40), ui.dp(24), ui.dp(32))
    }

    private fun setContent(content: LinearLayout, withLock: Boolean) {
        val frame = root ?: return
        frame.removeAllViews()
        val scroll = ScrollView(themed).apply {
            isFillViewport = true
            addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        frame.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        if (withLock) {
            frame.addView(lockButton(), FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END))
        }
    }

    private fun step(emoji: String, label: String, bg: Int, fg: Int) = LinearLayout(themed).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        background = ui.rounded(bg, 14)
        setPadding(0, ui.dp(10), 0, ui.dp(10))
        addView(ui.text(themed, emoji, 28f))
        addView(ui.text(themed, label, 14f, fg, bold = true).withTop(2))
        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
    }

    private fun arrow() = ui.text(themed, "→", 20f, Palette.MUTED).apply {
        setPadding(ui.dp(4), 0, ui.dp(4), 0)
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
    }

    private fun listenButton(onClick: () -> Unit) = TextView(themed).apply {
        text = "🔊  Ouvir de novo"
        textSize = 16f
        setTextColor(Palette.TEXT_SOFT)
        gravity = Gravity.CENTER
        background = ui.rounded(Palette.GRAY_BG, 24)
        setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(10))
        setOnClickListener { onClick() }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)

    private fun LinearLayout.LayoutParams.withTopMargin(topDp: Int) = apply { topMargin = ui.dp(topDp) }

    /** Define a margem superior de uma view que ainda não tem layoutParams próprios. */
    private fun <T : View> T.withTop(topDp: Int): T = apply {
        val lp = (layoutParams as? LinearLayout.LayoutParams) ?: LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        lp.topMargin = ui.dp(topDp)
        lp.gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = lp
    }
}
