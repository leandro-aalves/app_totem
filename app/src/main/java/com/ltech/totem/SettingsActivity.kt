package com.ltech.totem

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela de manutenção, só acessível depois de digitar a senha correta
 * no totem. Permite trocar a URL do sistema e a senha de saída.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyStoredOrientation()
        setContentView(R.layout.activity_settings)

        val urlInput = findViewById<EditText>(R.id.urlInput)
        val pinInput = findViewById<EditText>(R.id.newPinInput)
        val saveButton = findViewById<Button>(R.id.saveButton)
        val orientationButton = findViewById<Button>(R.id.orientationButton)
        val refreshButton = findViewById<Button>(R.id.refreshButton)
        val backButton = findViewById<Button>(R.id.backToKioskButton)

        urlInput.setText(ConfigManager.getUrl(this))
        updateOrientationButtonText(orientationButton)

        orientationButton.setOnClickListener {
            ConfigManager.toggleOrientation(this)
            updateOrientationButtonText(orientationButton)
            applyStoredOrientation()
        }

        saveButton.setOnClickListener {
            val newUrl = urlInput.text.toString().trim()
            if (newUrl.isNotEmpty()) {
                ConfigManager.setUrl(this, newUrl)
            }

            val newPin = pinInput.text.toString().trim()
            if (newPin.isNotEmpty()) {
                if (newPin.length in 4..8) {
                    ConfigManager.setPin(this, newPin)
                    pinInput.text.clear()
                } else {
                    Toast.makeText(this, "Senha deve ter de 4 a 8 dígitos", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            Toast.makeText(this, "Configurações salvas", Toast.LENGTH_SHORT).show()
        }

        refreshButton.setOnClickListener {
            ConfigManager.setForceReloadPending(this, true)
            Toast.makeText(this, "Atualizando totem...", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        backButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun updateOrientationButtonText(button: Button) {
        val isPortrait = ConfigManager.getOrientation(this) == ConfigManager.ORIENTATION_PORTRAIT
        button.text = if (isPortrait) "Orientação: Retrato" else "Orientação: Paisagem"
    }

    private fun applyStoredOrientation() {
        requestedOrientation = if (ConfigManager.getOrientation(this) == ConfigManager.ORIENTATION_PORTRAIT) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }
}
