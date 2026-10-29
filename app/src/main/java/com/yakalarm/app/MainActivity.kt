package com.yakalarm.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val meds = mutableListOf<Medicine>()
    private lateinit var adapter: MedAdapter
    private lateinit var list: RecyclerView
    private lateinit var emptyView: TextView
    private var previewTts: TextToSpeech? = null
    private var previewReady = false
    private var settingsDialogShowing = false

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyInsets(findViewById(R.id.root))
        AlarmService.createChannel(this)

        meds.addAll(MedStore.load(this))
        list = findViewById(R.id.medList)
        emptyView = findViewById(R.id.emptyText)
        adapter = MedAdapter(meds, onClick = { preview(it) }, onToggle = { med, on -> setEnabled(med, on) })
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        attachSwipeToDelete()

        stopRingingButton = findViewById(R.id.stopRingingButton)
        stopRingingButton.setOnClickListener {
            startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_STOP))
            stopRingingButton.visibility = View.GONE
        }
        findViewById<View>(R.id.addButton).setOnClickListener { showAddDialog() }
        findViewById<View>(R.id.deleteAllButton).setOnClickListener { confirmDeleteAll() }

        ContextCompat.registerReceiver(
            this, ringingReceiver,
            IntentFilter().apply {
                addAction(AlarmService.ACTION_STARTED)
                addAction(AlarmService.ACTION_CLOSED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        refreshList()
        AlarmScheduler.scheduleAll(this)
        previewTts = TextToSpeech(this, this)
    }

    private lateinit var stopRingingButton: View

    private val ringingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateRingingButton()
        }
    }

    private fun updateRingingButton() {
        stopRingingButton.visibility =
            if (AlarmService.activeNames.isNotEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        updateRingingButton()
        checkPermissionsStepByStep()
    }

    override fun onDestroy() {
        unregisterReceiver(ringingReceiver)
        previewTts?.shutdown()
        super.onDestroy()
    }

    /** 항상 시간순으로 정렬해서 화면 갱신 */
    @android.annotation.SuppressLint("NotifyDataSetChanged")
    private fun refreshList() {
        MedStore.sort(meds)
        adapter.notifyDataSetChanged()
        refreshEmpty()
    }

    // ---------- 알람 켜기/끄기 ----------
    private fun setEnabled(med: Medicine, on: Boolean) {
        val idx = meds.indexOfFirst { it.id == med.id }
        if (idx < 0 || meds[idx].enabled == on) return
        val updated = meds[idx].copy(enabled = on)
        meds[idx] = updated
        MedStore.save(this, meds)
        if (on) {
            AlarmScheduler.schedule(this, updated)
            Toast.makeText(this, "${updated.name} 알람을 켰어요 (${updated.timeText()})", Toast.LENGTH_SHORT).show()
        } else {
            AlarmScheduler.cancel(this, updated.id)
            Toast.makeText(this, "${updated.name} 알람을 껐어요", Toast.LENGTH_SHORT).show()
        }
        list.post { adapter.notifyItemChanged(idx) }
    }

    // ---------- 미리 듣기 ----------
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            previewTts?.setLanguage(Locale.KOREAN)
            previewReady = true
        }
    }

    private fun preview(med: Medicine) {
        if (!previewReady) {
            Toast.makeText(this, "음성 엔진을 준비 중이에요", Toast.LENGTH_SHORT).show()
            return
        }
        previewTts?.speak(med.name, TextToSpeech.QUEUE_FLUSH, null, "preview")
    }

    // ---------- 추가 ----------
    private fun showAddDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add, null)
        val nameInput = view.findViewById<EditText>(R.id.nameInput)
        val picker = view.findViewById<TimePicker>(R.id.timePicker)
        picker.setIs24HourView(false)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("약 알람 추가")
            .setView(view)
            .setPositiveButton("저장", null)
            .setNegativeButton("취소", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    nameInput.error = "약 이름을 입력하세요"
                    return@setOnClickListener
                }
                val med = Medicine(MedStore.nextId(this), name, picker.hour, picker.minute)
                addMed(med)
                Toast.makeText(this, "매일 ${med.timeText()}에 \"$name\"을(를) 외쳐요", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun addMed(med: Medicine) {
        meds.add(med)
        MedStore.sort(meds)
        MedStore.save(this, meds)
        if (med.enabled) AlarmScheduler.schedule(this, med)
        refreshList()
        list.scrollToPosition(meds.indexOfFirst { it.id == med.id }.coerceAtLeast(0))
    }

    // ---------- 옆으로 밀어서 삭제 ----------
    private fun attachSwipeToDelete() {
        val bgPaint = Paint().apply { color = 0xFFE53935.toInt() }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20f, resources.displayMetrics)
            isFakeBoldText = true
        }
        val pad = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28f, resources.displayMetrics)

        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val pos = vh.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val med = meds.removeAt(pos)
                adapter.notifyItemRemoved(pos)
                AlarmScheduler.cancel(this@MainActivity, med.id)
                MedStore.save(this@MainActivity, meds)
                refreshEmpty()
                Snackbar.make(list, "${med.name} 알람을 삭제했어요", Snackbar.LENGTH_LONG)
                    .setAction("되돌리기") { addMed(med) }
                    .show()
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val v = vh.itemView
                if (dX != 0f) {
                    val top = v.top.toFloat()
                    val bottom = v.bottom.toFloat()
                    val textY = (top + bottom) / 2 - (textPaint.descent() + textPaint.ascent()) / 2
                    if (dX > 0) {
                        c.drawRect(v.left.toFloat(), top, v.left + dX, bottom, bgPaint)
                        textPaint.textAlign = Paint.Align.LEFT
                        c.drawText("삭제", v.left + pad, textY, textPaint)
                    } else {
                        c.drawRect(v.right + dX, top, v.right.toFloat(), bottom, bgPaint)
                        textPaint.textAlign = Paint.Align.RIGHT
                        c.drawText("삭제", v.right - pad, textY, textPaint)
                    }
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(list)
    }

    // ---------- 모두 삭제 ----------
    private fun confirmDeleteAll() {
        if (meds.isEmpty()) {
            Toast.makeText(this, "삭제할 알람이 없어요", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("모두 삭제")
            .setMessage("약 알람 ${meds.size}개를 모두 삭제할까요?")
            .setPositiveButton("모두 삭제") { _, _ ->
                meds.forEach { AlarmScheduler.cancel(this, it.id) }
                meds.clear()
                MedStore.save(this, meds)
                refreshList()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun refreshEmpty() {
        emptyView.visibility = if (meds.isEmpty()) View.VISIBLE else View.GONE
    }

    // ---------- 권한 안내 (하나씩 순서대로) ----------
    private fun checkPermissionsStepByStep() {
        if (settingsDialogShowing) return
        val prefs = getSharedPreferences("setup", MODE_PRIVATE)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("asked_notif", false)
        ) {
            prefs.edit().putBoolean("asked_notif", true).apply()
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms() && !prefs.getBoolean("asked_exact", false)) {
                prefs.edit().putBoolean("asked_exact", true).apply()
                askSettings(
                    "정확한 시간에 알람이 울리려면 '알람 및 리마인더' 권한을 허용해 주세요.",
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                )
                return
            }
        }

        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent() && !prefs.getBoolean("asked_fullscreen", false)) {
                prefs.edit().putBoolean("asked_fullscreen", true).apply()
                askSettings(
                    "화면이 꺼져 있어도 알람 화면이 뜨도록 '전체 화면 알림'을 허용해 주세요.",
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                )
                return
            }
        }

        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName) && !prefs.getBoolean("asked_battery", false)) {
            prefs.edit().putBoolean("asked_battery", true).apply()
            askSettings(
                "갤럭시의 절전 기능 때문에 알람이 늦거나 안 울릴 수 있어요. 배터리 제한을 해제할까요?",
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun askSettings(message: String, intent: Intent) {
        settingsDialogShowing = true
        MaterialAlertDialogBuilder(this)
            .setTitle("설정이 필요해요")
            .setMessage(message)
            .setPositiveButton("설정하기") { _, _ ->
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                    )
                }
            }
            .setNegativeButton("나중에", null)
            .setOnDismissListener {
                settingsDialogShowing = false
                list.post { checkPermissionsStepByStep() }
            }
            .show()
    }
}
