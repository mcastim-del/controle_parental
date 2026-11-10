package br.com.controleparental

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Tela de bloqueio desenhada por cima de todos os apps quando o tempo acaba.
 * Só sai com o PIN do responsável (concedendo tempo extra) ou na virada do dia.
 */
class LockOverlay(
    private val context: Context,
    private val prefs: Prefs,
    private val onUnlocked: () -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private var infoText: TextView? = null

    val isShowing: Boolean get() = root != null

    /** Mostra o bloqueio. Retorna false se não houver permissão de sobreposição. */
    fun show(): Boolean {
        if (root != null) {
            refresh()
            return true
        }
        if (!Settings.canDrawOverlays(context)) return false

        val view = buildView()
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
            wm.addView(view, params)
            root = view
            refresh()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        infoText = null
    }

    private fun refresh() {
        infoText?.text = "Hoje você usou ${formatDuration(prefs.usedSeconds)} de tela.\n" +
            "O tempo será liberado novamente à meia-noite."
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private fun buildView(): View {
        val ctx = ContextThemeWrapper(context, android.R.style.Theme_Material)

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(48), dp(32), dp(48))
        }

        fun text(value: String, sizeSp: Float, bold: Boolean = false) = TextView(ctx).apply {
            text = value
            textSize = sizeSp
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(8))
        }

        column.addView(text("⏰", 64f))
        column.addView(text("Tempo de tela esgotado", 26f, bold = true))
        infoText = text("", 16f).also { column.addView(it) }

        column.addView(text("Responsável: digite o PIN para liberar mais tempo", 14f).apply {
            setPadding(0, dp(32), 0, dp(4))
        })

        val pin = EditText(ctx).apply {
            hint = "PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(8))
            gravity = Gravity.CENTER
            textSize = 22f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        column.addView(pin, LinearLayout.LayoutParams(dp(200), WRAP_CONTENT))

        val status = text("", 14f).apply { setTextColor(Color.rgb(255, 138, 128)) }
        column.addView(status)

        fun grant(extraSeconds: Long?) {
            when (val r = prefs.checkPin(pin.text.toString())) {
                is PinCheck.Ok -> {
                    if (extraSeconds == null) prefs.unlimitedToday = true
                    else prefs.grantExtra(extraSeconds)
                    pin.setText("")
                    context.getSystemService(InputMethodManager::class.java)
                        .hideSoftInputFromWindow(pin.windowToken, 0)
                    onUnlocked()
                }
                is PinCheck.Wrong -> {
                    status.text = "PIN incorreto. Tentativas restantes: ${r.attemptsLeft}"
                    pin.setText("")
                }
                is PinCheck.Locked -> {
                    status.text = "Muitas tentativas. Aguarde ${r.seconds} segundos."
                    pin.setText("")
                }
            }
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("+15 min" to 15 * 60L, "+30 min" to 30 * 60L, "+1 hora" to 60 * 60L).forEach { (label, secs) ->
            row.addView(Button(ctx).apply {
                text = label
                setOnClickListener { grant(secs) }
            })
        }
        column.addView(row)
        column.addView(Button(ctx).apply {
            text = "Liberar o resto do dia"
            setOnClickListener { grant(null) }
        })

        return ScrollView(ctx).apply {
            setBackgroundColor(Color.rgb(26, 35, 126))
            isFillViewport = true
            addView(column, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    }
}
