package br.com.controleparental

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Tela do responsável: criar PIN, entrar com PIN e configurar os limites. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private var unlocked = false
    private var changingPin = false
    private var stoppedAt = 0L

    private val refresher = object : Runnable {
        override fun run() {
            renderUsage()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        findViewById<Button>(R.id.btnCreatePin).setOnClickListener { createPin() }
        findViewById<Button>(R.id.btnLogin).setOnClickListener { login() }
        findViewById<Button>(R.id.btnSave).setOnClickListener { saveLimits() }
        findViewById<Button>(R.id.btnExtra30).setOnClickListener {
            prefs.grantExtra(30 * 60L)
            ScreenTimeService.start(this)
            toast("+30 minutos liberados para hoje")
        }
        findViewById<Button>(R.id.btnResetToday).setOnClickListener {
            prefs.resetToday()
            ScreenTimeService.start(this)
            toast("Tempo de hoje zerado")
        }
        findViewById<Switch>(R.id.switchEnabled).setOnCheckedChangeListener { _, checked ->
            if (prefs.enabled != checked) {
                prefs.enabled = checked
                ScreenTimeService.start(this)
            }
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btnNotifications).setOnClickListener { requestNotifications() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }
        findViewById<Button>(R.id.btnAdmin).setOnClickListener {
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent())
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Impede que o Controle Parental seja desinstalado ou parado sem o responsável."
                )
            })
        }
        findViewById<Button>(R.id.btnPreview).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                toast("Ative primeiro a permissão 1 (sobrepor outros apps)")
            } else {
                ScreenTimeService.preview(this)
            }
        }
        findViewById<Button>(R.id.btnChangePin).setOnClickListener {
            changingPin = true
            render()
        }
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            unlocked = false
            render()
        }
    }

    override fun onStart() {
        super.onStart()
        // Se o app ficou fora da tela por mais de 1 minuto, pede o PIN de novo.
        if (unlocked && stoppedAt != 0L && System.currentTimeMillis() - stoppedAt > RELOCK_MS) {
            unlocked = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (prefs.hasPin()) ScreenTimeService.start(this)
        render()
        handler.post(refresher)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        parentSessionActive = false
        super.onPause()
    }

    override fun onStop() {
        stoppedAt = System.currentTimeMillis()
        super.onStop()
    }

    // ---------- Telas ----------

    private fun render() {
        val create = !prefs.hasPin() || (unlocked && changingPin)
        val settings = !create && unlocked
        val login = !create && !unlocked
        parentSessionActive = settings

        show(R.id.sectionCreatePin, create)
        show(R.id.sectionLogin, login)
        show(R.id.sectionSettings, settings)

        if (create) {
            findViewById<TextView>(R.id.txtCreateTitle).text =
                if (changingPin) "Alterar o PIN do responsável" else "Crie o PIN do responsável"
        }
        if (settings) {
            findViewById<Switch>(R.id.switchEnabled).isChecked = prefs.enabled
            findViewById<EditText>(R.id.edtWeekday).setText(prefs.weekdayLimitMin.toString())
            findViewById<EditText>(R.id.edtWeekend).setText(prefs.weekendLimitMin.toString())
            renderPermissions()
        }
        renderUsage()
    }

    private fun show(id: Int, visible: Boolean) {
        findViewById<View>(id).visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun usageText(): String {
        prefs.ensureToday()
        val used = formatDuration(prefs.usedSeconds)
        val which = if (prefs.isWeekendToday()) "sábado e domingo" else "segunda a sexta"
        val limitLine = "\nLimite de hoje: ${formatDuration(prefs.limitSecondsToday())} ($which)" +
            (if (prefs.bonusSeconds > 0) " + extra liberado" else "") +
            "\nO tempo só conta com a tela ligada."
        return when {
            !prefs.enabled -> "Usado hoje: $used • controle desativado"
            prefs.unlimitedToday -> "Usado hoje: $used • sem limite hoje"
            else -> {
                val remaining = prefs.remainingSeconds()
                if (remaining <= 0) "Usado hoje: $used • tempo esgotado$limitLine"
                else "Usado hoje: $used • restam ${formatDuration(remaining)}$limitLine"
            }
        }
    }

    private fun renderUsage() {
        if (!prefs.hasPin()) return
        val text = usageText()
        findViewById<TextView>(R.id.txtUsage).text = text
        findViewById<TextView>(R.id.txtLoginUsage).text = text
    }

    private fun renderPermissions() {
        fun mark(ok: Boolean) = if (ok) "✅" else "❌"
        val overlay = Settings.canDrawOverlays(this)
        val notifications = getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        val battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val admin = getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent())
        findViewById<TextView>(R.id.txtPermissions).text = """
            ${mark(overlay)} Sobrepor outros apps (obrigatório para bloquear a tela)
            ${mark(notifications)} Notificações (mostra o tempo restante)
            ${mark(battery)} Sem otimização de bateria (evita que o sistema pare a contagem)
            ${mark(admin)} Proteção contra desinstalação
        """.trimIndent()
    }

    // ---------- Ações ----------

    private fun createPin() {
        val pin = findViewById<EditText>(R.id.edtNewPin).text.toString()
        val confirm = findViewById<EditText>(R.id.edtConfirmPin).text.toString()
        val error = findViewById<TextView>(R.id.txtCreateError)
        when {
            pin.length !in 4..8 || !pin.all { it.isDigit() } -> error.text = "O PIN deve ter de 4 a 8 números."
            pin != confirm -> error.text = "Os PINs não conferem."
            else -> {
                val firstTime = !prefs.hasPin()
                prefs.setPin(pin)
                error.text = ""
                findViewById<EditText>(R.id.edtNewPin).setText("")
                findViewById<EditText>(R.id.edtConfirmPin).setText("")
                changingPin = false
                unlocked = true
                toast("PIN salvo")
                ScreenTimeService.start(this)
                if (firstTime) requestNotifications()
                render()
            }
        }
    }

    private fun login() {
        val field = findViewById<EditText>(R.id.edtLoginPin)
        val error = findViewById<TextView>(R.id.txtLoginError)
        when (val r = prefs.checkPin(field.text.toString())) {
            is PinCheck.Ok -> {
                error.text = ""
                unlocked = true
                render()
            }
            is PinCheck.Wrong -> error.text = "PIN incorreto. Tentativas restantes: ${r.attemptsLeft}"
            is PinCheck.Locked -> error.text = "Muitas tentativas. Aguarde ${r.seconds} segundos."
        }
        field.setText("")
    }

    private fun saveLimits() {
        val weekday = findViewById<EditText>(R.id.edtWeekday).text.toString().toIntOrNull()
        val weekend = findViewById<EditText>(R.id.edtWeekend).text.toString().toIntOrNull()
        if (weekday == null || weekend == null || weekday !in 1..1440 || weekend !in 1..1440) {
            toast("Informe minutos entre 1 e 1440")
            return
        }
        prefs.weekdayLimitMin = weekday
        prefs.weekendLimitMin = weekend
        // Um limite novo vale como está: apaga tempo extra ou "liberar o resto do dia" de hoje.
        prefs.bonusSeconds = 0
        prefs.unlimitedToday = false
        prefs.warnedLevel = 0
        ScreenTimeService.start(this)
        toast("Limites salvos (tempo extra de hoje removido)")
        renderUsage()
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        render()
    }

    private fun adminComponent() = ComponentName(this, AdminReceiver::class.java)

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val RELOCK_MS = 60_000L

        /** true enquanto o responsável está com as configurações abertas. */
        @Volatile
        var parentSessionActive = false
    }
}
