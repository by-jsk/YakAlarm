package com.yakalarm.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** 매일 같은 시각에 울리도록 시스템 알람을 등록/취소 */
object AlarmScheduler {
    const val EXTRA_ID = "med_id"

    private fun alarmIntent(ctx: Context, id: Int): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            ctx, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun nextTrigger(hour: Int, minute: Int, after: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (c.timeInMillis <= after) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun schedule(ctx: Context, med: Medicine, after: Long = System.currentTimeMillis()) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = nextTrigger(med.hour, med.minute, after)
        val pi = alarmIntent(ctx, med.id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            return
        }
        val showIntent = PendingIntent.getActivity(
            ctx, med.id, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pi)
    }

    fun cancel(ctx: Context, id: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(alarmIntent(ctx, id))
    }

    fun scheduleAll(ctx: Context) {
        MedStore.load(ctx).forEach {
            if (it.enabled) schedule(ctx, it) else cancel(ctx, it.id)
        }
    }
}
