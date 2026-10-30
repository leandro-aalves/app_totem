package com.ltech.totem

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Dispara o refresh automático diário do totem (de madrugada, quando o
 * movimento é mínimo) e reagenda o alarme depois de um reboot do tablet
 * (AlarmManager perde todos os alarmes agendados quando o aparelho desliga).
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> scheduleDailyRefresh(context)
            ACTION_DAILY_REFRESH -> triggerRefresh(context)
        }
    }

    private fun triggerRefresh(context: Context) {
        ConfigManager.setForceReloadPending(context, true)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        context.startActivity(launchIntent)
    }

    companion object {
        private const val ACTION_DAILY_REFRESH = "com.ltech.totem.ACTION_DAILY_REFRESH"
        private const val REQUEST_CODE = 1001

        // Horário do refresh automático: 3h da manhã (baixo movimento no totem).
        private const val REFRESH_HOUR = 3
        private const val REFRESH_MINUTE = 0

        fun scheduleDailyRefresh(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_DAILY_REFRESH
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                nextTriggerTimeMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }

        private fun nextTriggerTimeMillis(): Long {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, REFRESH_HOUR)
                set(Calendar.MINUTE, REFRESH_MINUTE)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            return calendar.timeInMillis
        }
    }
}
