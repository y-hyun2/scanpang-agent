package com.scanpang.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.scanpang.app.R
import com.scanpang.app.data.AppSettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PRAYER        = "com.scanpang.app.PRAYER_ALARM"
        const val ACTION_DAILY_REFRESH = "com.scanpang.app.DAILY_REFRESH"
        const val EXTRA_PRAYER_NAME    = "prayer_name"
        const val CHANNEL_ID           = "prayer_time_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = AppSettingsPreferences(context)

        when (intent.action) {
            ACTION_PRAYER -> {
                if (!prefs.isPushEnabled() || !prefs.isPrayerAlarmEnabled()) return
                val name = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: return
                showNotification(context, name)
            }

            // 매일 자정 00:05 또는 부팅 후 — 오늘치 알람 재예약
            ACTION_DAILY_REFRESH, Intent.ACTION_BOOT_COMPLETED -> {
                if (!prefs.isPrayerAlarmEnabled()) return
                val lat = prefs.getLastKnownLat()
                val lng = prefs.getLastKnownLng()
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        PrayerAlarmScheduler.scheduleForToday(context, lat, lng)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun showNotification(context: Context, prayerName: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("기도 시간 알림")
            .setContentText("$prayerName 기도 시간 10분 전입니다")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(prayerName.hashCode(), notification)
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "기도 시간 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "기도 시간 10분 전 알림"
            }
        )
    }
}
