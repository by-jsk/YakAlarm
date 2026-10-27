package com.yakalarm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** 알람 시각이 되면 호출됨 → 내일 알람 예약 + 음성 알람 시작 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ID, -1)
        val med = MedStore.find(context, id) ?: return

        // 다음 날 같은 시각으로 다시 예약
        AlarmScheduler.schedule(context, med, System.currentTimeMillis() + 30_000)

        val service = Intent(context, AlarmService::class.java)
            .putExtra(AlarmService.EXTRA_NAME, med.name)
        ContextCompat.startForegroundService(context, service)
    }
}

/** 휴대폰 재부팅·시간 변경·앱 업데이트 후 알람 다시 등록 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmScheduler.scheduleAll(context)
    }
}
