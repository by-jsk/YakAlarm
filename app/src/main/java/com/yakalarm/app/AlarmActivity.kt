package com.yakalarm.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** 잠금 화면 위에 뜨는 알람 화면 (큰 '끄기' 버튼) */
class AlarmActivity : AppCompatActivity() {

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_alarm)
        applyInsets(findViewById(R.id.alarmRoot))

        findViewById<View>(R.id.stopButton).setOnClickListener {
            startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_STOP))
            finish()
        }

        ContextCompat.registerReceiver(
            this, closeReceiver, IntentFilter(AlarmService.ACTION_CLOSED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onResume() {
        super.onResume()
        val names = AlarmService.activeNames
        if (names.isEmpty()) {
            finish()
            return
        }
        findViewById<TextView>(R.id.alarmNames).text = names.joinToString("\n")
    }

    override fun onDestroy() {
        unregisterReceiver(closeReceiver)
        super.onDestroy()
    }
}

fun applyInsets(root: View) {
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
        )
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
}
