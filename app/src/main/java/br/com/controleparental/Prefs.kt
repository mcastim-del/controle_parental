package br.com.controleparental

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.DayOfWeek
import java.time.LocalDate

/** Resultado da verificação do PIN do responsável. */
sealed class PinCheck {
    object Ok : PinCheck()
    data class Wrong(val attemptsLeft: Int) : PinCheck()
    data class Locked(val seconds: Long) : PinCheck()
}

/** Armazena configurações, PIN e o tempo de tela usado no dia. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("controle_parental", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(K_ENABLED, true)
        set(v) = sp.edit().putBoolean(K_ENABLED, v).apply()

    /** Limite diário de segunda a sexta, em minutos. */
    var weekdayLimitMin: Int
        get() = sp.getInt(K_WEEKDAY, 120)
        set(v) = sp.edit().putInt(K_WEEKDAY, v).apply()

    /** Limite diário de sábado e domingo, em minutos. */
    var weekendLimitMin: Int
        get() = sp.getInt(K_WEEKEND, 180)
        set(v) = sp.edit().putInt(K_WEEKEND, v).apply()

    var usedSeconds: Long
        get() = sp.getLong(K_USED, 0)
        set(v) = sp.edit().putLong(K_USED, v).apply()

    var bonusSeconds: Long
        get() = sp.getLong(K_BONUS, 0)
        set(v) = sp.edit().putLong(K_BONUS, v).apply()

    var unlimitedToday: Boolean
        get() = sp.getBoolean(K_UNLIMITED, false)
        set(v) = sp.edit().putBoolean(K_UNLIMITED, v).apply()

    /** 0 = nenhum aviso; 1 = avisou 5 min; 2 = avisou 1 min. */
    var warnedLevel: Int
        get() = sp.getInt(K_WARNED, 0)
        set(v) = sp.edit().putInt(K_WARNED, v).apply()

    /** Zera os contadores quando o dia muda. Retorna true se zerou. */
    fun ensureToday(): Boolean {
        val today = LocalDate.now().toString()
        if (sp.getString(K_DAY, null) == today) return false
        sp.edit()
            .putString(K_DAY, today)
            .putLong(K_USED, 0)
            .putLong(K_BONUS, 0)
            .putBoolean(K_UNLIMITED, false)
            .putInt(K_WARNED, 0)
            .apply()
        return true
    }

    fun isWeekendToday(): Boolean {
        val day = LocalDate.now().dayOfWeek
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
    }

    fun limitSecondsToday(): Long =
        (if (isWeekendToday()) weekendLimitMin else weekdayLimitMin) * 60L

    /** Segundos restantes hoje (Long.MAX_VALUE quando não há limite). */
    fun remainingSeconds(): Long =
        if (!enabled || unlimitedToday) Long.MAX_VALUE
        else limitSecondsToday() + bonusSeconds - usedSeconds

    /** Concede tempo extra hoje: o restante passa a ser (restante atual, mínimo 0) + extra. */
    fun grantExtra(seconds: Long) {
        val current = (limitSecondsToday() + bonusSeconds - usedSeconds).coerceAtLeast(0)
        bonusSeconds = usedSeconds - limitSecondsToday() + current + seconds
        warnedLevel = 0
    }

    fun resetToday() {
        sp.edit().putLong(K_USED, 0).putLong(K_BONUS, 0)
            .putBoolean(K_UNLIMITED, false).putInt(K_WARNED, 0).apply()
    }

    // ---------- PIN ----------

    fun hasPin(): Boolean = sp.contains(K_PIN_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        sp.edit()
            .putString(K_PIN_SALT, salt)
            .putString(K_PIN_HASH, hash(salt, pin))
            .putInt(K_FAILED, 0)
            .putLong(K_LOCKOUT, 0)
            .apply()
    }

    fun checkPin(pin: String): PinCheck {
        val now = System.currentTimeMillis()
        val lockoutUntil = sp.getLong(K_LOCKOUT, 0)
        if (now < lockoutUntil) return PinCheck.Locked((lockoutUntil - now) / 1000 + 1)

        val salt = sp.getString(K_PIN_SALT, "") ?: ""
        val stored = sp.getString(K_PIN_HASH, null) ?: return PinCheck.Wrong(MAX_ATTEMPTS)
        val ok = MessageDigest.isEqual(hash(salt, pin).toByteArray(), stored.toByteArray())
        if (ok) {
            sp.edit().putInt(K_FAILED, 0).apply()
            return PinCheck.Ok
        }
        val failed = sp.getInt(K_FAILED, 0) + 1
        return if (failed >= MAX_ATTEMPTS) {
            sp.edit().putInt(K_FAILED, 0).putLong(K_LOCKOUT, now + LOCKOUT_MS).apply()
            PinCheck.Locked(LOCKOUT_MS / 1000)
        } else {
            sp.edit().putInt(K_FAILED, failed).apply()
            PinCheck.Wrong(MAX_ATTEMPTS - failed)
        }
    }

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    companion object {
        private const val K_ENABLED = "enabled"
        private const val K_WEEKDAY = "weekday_limit_min"
        private const val K_WEEKEND = "weekend_limit_min"
        private const val K_USED = "used_seconds"
        private const val K_BONUS = "bonus_seconds"
        private const val K_UNLIMITED = "unlimited_today"
        private const val K_WARNED = "warned_level"
        private const val K_DAY = "day"
        private const val K_PIN_HASH = "pin_hash"
        private const val K_PIN_SALT = "pin_salt"
        private const val K_FAILED = "pin_failed"
        private const val K_LOCKOUT = "pin_lockout_until"
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_MS = 60_000L
    }
}

/** Formata segundos como "1h 05min" ou "12min". */
fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    if (s in 1L..59L) return "menos de 1min"
    return if (h > 0) "${h}h %02dmin".format(m) else "${m}min"
}
