package com.scanpang.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.scanpang.app.data.remote.HalalRequest
import com.scanpang.app.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object PrayerAlarmScheduler {

    private const val TAG = "PrayerAlarmScheduler"

    private val PRAYERS = listOf(
        Triple("파즈르", "fajr",    1001),
        Triple("두흐르", "dhuhr",   1002),
        Triple("아스르", "asr",     1003),
        Triple("마그립", "maghrib", 1004),
        Triple("이샤",   "isha",    1005),
    )
    private const val REQ_DAILY_REFRESH = 1010

    /**
     * 오늘 남은 기도 시간 각 10분 전 알람을 예약한다.
     * 자정 00:05 에 내일치 재예약 알람도 함께 건다.
     */
    suspend fun scheduleForToday(context: Context, lat: Double, lng: Double) = withContext(Dispatchers.IO) {
        try {
            val response = RetrofitClient.api.queryHalal(
                HalalRequest(category = "prayer_time", lat = lat, lng = lng)
            )
            val pt = response.prayer_times ?: return@withContext
            val times = listOf(pt.fajr, pt.dhuhr, pt.asr, pt.maghrib, pt.isha)

            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val now = LocalDateTime.now()

            PRAYERS.forEachIndexed { i, (prayerName, _, reqCode) ->
                val notifyAt = parseNotifyTime(times[i]) ?: return@forEachIndexed
                if (notifyAt.isAfter(now)) {
                    scheduleExact(context, am, notifyAt, prayerName, reqCode)
                    Log.d(TAG, "Scheduled $prayerName @ $notifyAt")
                }
            }
            scheduleDailyRefresh(context, am)
        } catch (e: Exception) {
            Log.e(TAG, "scheduleForToday FAILED", e)
        }
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        PRAYERS.forEach { (_, _, reqCode) ->
            PendingIntent.getBroadcast(
                context, reqCode,
                Intent(context, PrayerAlarmReceiver::class.java).apply { action = PrayerAlarmReceiver.ACTION_PRAYER },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let { am.cancel(it) }
        }
        PendingIntent.getBroadcast(
            context, REQ_DAILY_REFRESH,
            Intent(context, PrayerAlarmReceiver::class.java).apply { action = PrayerAlarmReceiver.ACTION_DAILY_REFRESH },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { am.cancel(it) }
        Log.d(TAG, "All prayer alarms cancelled")
    }

    // "HH:mm" 또는 "HH:mm (KST)" 형식 파싱 → 기도 시간 10분 전 LocalDateTime
    private fun parseNotifyTime(timeStr: String): LocalDateTime? = runCatching {
        val time = LocalTime.parse(timeStr.trim().take(5), DateTimeFormatter.ofPattern("HH:mm"))
        LocalDate.now().atTime(time).minusMinutes(10)
    }.getOrNull()

    private fun scheduleExact(
        context: Context,
        am: AlarmManager,
        triggerAt: LocalDateTime,
        prayerName: String,
        reqCode: Int,
    ) {
        val triggerMs = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = PrayerAlarmReceiver.ACTION_PRAYER
            putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_NAME, prayerName)
        }
        val pi = PendingIntent.getBroadcast(
            context, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
    }

    private fun scheduleDailyRefresh(context: Context, am: AlarmManager) {
        val triggerMs = LocalDate.now().plusDays(1).atTime(0, 5)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = PendingIntent.getBroadcast(
            context, REQ_DAILY_REFRESH,
            Intent(context, PrayerAlarmReceiver::class.java).apply { action = PrayerAlarmReceiver.ACTION_DAILY_REFRESH },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.set(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
    }
}
