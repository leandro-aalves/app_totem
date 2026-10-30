package com.ltech.totem

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receiver obrigatório para o app virar Device Admin / Device Owner.
 * Configurado via: adb shell dpm set-device-owner com.ltech.totem/.TotemDeviceAdminReceiver
 */
class TotemDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i("TotemDeviceAdmin", "Device admin habilitado")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i("TotemDeviceAdmin", "Device admin desabilitado")
    }
}
