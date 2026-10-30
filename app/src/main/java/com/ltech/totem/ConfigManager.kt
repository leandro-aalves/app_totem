package com.ltech.totem

import android.content.Context
import java.security.MessageDigest

/**
 * Guarda a URL do totem e a senha (como hash) em SharedPreferences.
 * Na primeira execução define a senha padrão "1234" — deve ser trocada
 * pela tela de configurações antes de o tablet ir pra produção.
 */
object ConfigManager {

    private const val PREFS_NAME = "totem_config"
    private const val KEY_URL = "totem_url"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_ORIENTATION = "orientation"
    private const val KEY_FORCE_RELOAD = "force_reload_pending"

    private const val DEFAULT_URL = "http://suavez.heliomarb.com.br/totem"
    private const val DEFAULT_PIN = "1234"

    const val ORIENTATION_LANDSCAPE = "landscape"
    const val ORIENTATION_PORTRAIT = "portrait"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getUrl(context: Context): String =
        prefs(context).getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL

    fun setUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_URL, url).apply()
    }

    fun setPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_PIN_HASH, hash(pin)).apply()
    }

    fun checkPin(context: Context, pin: String): Boolean =
        hash(pin) == getPinHash(context)

    fun getOrientation(context: Context): String =
        prefs(context).getString(KEY_ORIENTATION, ORIENTATION_LANDSCAPE) ?: ORIENTATION_LANDSCAPE

    fun toggleOrientation(context: Context): String {
        val newValue = if (getOrientation(context) == ORIENTATION_LANDSCAPE)
            ORIENTATION_PORTRAIT
        else
            ORIENTATION_LANDSCAPE
        prefs(context).edit().putString(KEY_ORIENTATION, newValue).apply()
        return newValue
    }

    /**
     * Sinaliza que a WebView deve recarregar a URL do zero (ignorando cache)
     * na próxima vez que a MainActivity ficar em primeiro plano. Usado tanto
     * pelo botão "Atualizar Totem" das Configurações quanto pelo refresh
     * automático diário (AlarmReceiver).
     */
    fun setForceReloadPending(context: Context, pending: Boolean) {
        prefs(context).edit().putBoolean(KEY_FORCE_RELOAD, pending).apply()
    }

    fun isForceReloadPending(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FORCE_RELOAD, false)

    private fun getPinHash(context: Context): String {
        val stored = prefs(context).getString(KEY_PIN_HASH, null)
        if (stored != null) return stored
        val defaultHash = hash(DEFAULT_PIN)
        prefs(context).edit().putString(KEY_PIN_HASH, defaultHash).apply()
        return defaultHash
    }

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
