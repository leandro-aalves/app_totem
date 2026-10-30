package com.ltech.totem

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela única do totem: WebView em tela cheia carregando o sistema existente,
 * travada em modo Kiosk (Lock Task) para não deixar o usuário sair do app,
 * com tela sempre ligada e botões físicos de volume/voltar bloqueados.
 *
 * Para sair do modo totem: tocar 5x no canto superior direito (invisível)
 * e digitar a senha configurada em ConfigManager / SettingsActivity.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private var tapCount = 0
    private val tapResetHandler = Handler(Looper.getMainLooper())
    private val tapResetRunnable = Runnable { tapCount = 0 }

    private val reloadHandler = Handler(Looper.getMainLooper())

    /**
     * O botão físico de Power é tratado pelo sistema operacional antes de
     * chegar a qualquer app — não existe API pública (nem para Device Owner)
     * que impeça um toque no Power de apagar a tela. O que dá pra fazer, e é
     * a mesma técnica usada por soluções comerciais de kiosk, é detectar o
     * instante em que a tela apaga (ACTION_SCREEN_OFF) e acender de volta na
     * hora, sem pedir senha de desbloqueio (keyguard desativado via Device
     * Owner). Na prática vira um "pisca" rápido em vez de travar de verdade.
     */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                wakeScreenBackUp()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        allowShowingOverKeyguard()
        hideSystemUI()
        applyStoredOrientation()

        setContentView(R.layout.activity_main)

        adminComponent = ComponentName(this, TotemDeviceAdminReceiver::class.java)
        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager

        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        setupWebView()
        setupUnlockGesture()
        registerAsHomeIfOwner()
        enterKioskMode()
        AlarmReceiver.scheduleDailyRefresh(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkAndApplyForceReload()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        applyStoredOrientation()
        enterKioskMode()
        checkAndApplyForceReload()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    @Suppress("DEPRECATION")
    private fun allowShowingOverKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenBackUp() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                or PowerManager.ACQUIRE_CAUSES_WAKEUP
                or PowerManager.ON_AFTER_RELEASE,
            "totem:wake-on-power-button"
        )
        wakeLock.acquire(3000L)
        wakeLock.release()
    }

    private fun applyStoredOrientation() {
        requestedOrientation = if (ConfigManager.getOrientation(this) == ConfigManager.ORIENTATION_PORTRAIT) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    private fun isDeviceOwner(): Boolean = devicePolicyManager.isDeviceOwnerApp(packageName)

    private fun isInLockTaskMode(): Boolean {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    @Suppress("DEPRECATION")
    private fun enterKioskMode() {
        if (isDeviceOwner()) {
            devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf(packageName))
            devicePolicyManager.setStatusBarDisabled(adminComponent, true)
            try {
                devicePolicyManager.setKeyguardDisabled(adminComponent, true)
            } catch (_: Exception) {
                // Alguns fabricantes/versões não permitem desativar o keyguard;
                // o wakeScreenBackUp() + showWhenLocked ainda cobrem a maior parte dos casos.
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Sem isso, o Android mantém o menu de "Desligar/Reiniciar" (Global
                // Actions) disponível ao segurar o Power mesmo em Lock Task.
                // LOCK_TASK_FEATURE_NONE desativa esse menu e todos os outros
                // recursos opcionais (home, recentes, notificações, etc).
                devicePolicyManager.setLockTaskFeatures(
                    adminComponent,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE
                )
            }
        }
        if (!isInLockTaskMode()) {
            try {
                startLockTask()
            } catch (_: Exception) {
                // Se o app ainda não é Device Owner, cai no screen pinning
                // simples (o usuário consegue sair segurando Voltar+Recentes).
            }
        }
    }

    private fun registerAsHomeIfOwner() {
        if (!isDeviceOwner()) return
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        devicePolicyManager.addPersistentPreferredActivity(
            adminComponent, filter, ComponentName(this, MainActivity::class.java)
        )
    }

    private fun setupWebView() {
        webView = findViewById(R.id.webView)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                scheduleReload()
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                // Sistema interno/rede local às vezes usa certificado próprio.
                // Se o seu sistema usa certificado público válido, troque para handler.cancel().
                handler.proceed()
            }
        }

        loadTotemUrl()
    }

    private fun loadTotemUrl() {
        webView.loadUrl(ConfigManager.getUrl(this))
    }

    /**
     * Checado tanto em onResume() quanto onNewIntent(): como a MainActivity
     * fica praticamente sempre em primeiro plano (é o Home do kiosk), trazer
     * a activity de volta via Intent (botão "Atualizar" ou alarme noturno)
     * pode disparar só um dos dois callbacks, nunca os dois junto.
     */
    private fun checkAndApplyForceReload() {
        if (ConfigManager.isForceReloadPending(this)) {
            ConfigManager.setForceReloadPending(this, false)
            forceReloadWebView()
        }
    }

    private fun forceReloadWebView() {
        if (!::webView.isInitialized) return
        webView.clearCache(true)
        loadTotemUrl()
    }

    private fun scheduleReload() {
        reloadHandler.removeCallbacksAndMessages(null)
        reloadHandler.postDelayed({ loadTotemUrl() }, 5000)
    }

    private fun setupUnlockGesture() {
        val tapArea = findViewById<View>(R.id.unlockTapArea)
        tapArea.setOnClickListener {
            tapCount++
            tapResetHandler.removeCallbacks(tapResetRunnable)
            if (tapCount >= 5) {
                tapCount = 0
                showPasswordDialog()
            } else {
                tapResetHandler.postDelayed(tapResetRunnable, 2500)
            }
        }
    }

    private fun showPasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_password, null)
        val input = view.findViewById<EditText>(R.id.pinInput)

        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton(R.string.action_ok) { _, _ ->
                if (ConfigManager.checkPin(this, input.text.toString())) {
                    exitKioskMode()
                } else {
                    Toast.makeText(this, R.string.wrong_pin, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun exitKioskMode() {
        if (isInLockTaskMode()) {
            stopLockTask()
        }
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_BACK -> return true
        }
        return super.dispatchKeyEvent(event)
    }

    @Suppress("OVERRIDE_DEPRECATION", "MissingSuperCall")
    override fun onBackPressed() {
        // Intencionalmente vazio: bloqueia o botão de voltar no modo totem.
    }
}
