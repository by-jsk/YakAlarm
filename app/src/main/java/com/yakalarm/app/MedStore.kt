package com.yakalarm.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class Medicine(val id: Int, val name: String, val hour: Int, val minute: Int) {
    fun timeText(): String {
        val ampm = if (hour < 12) "오전" else "오후"
        val h = if (hour % 12 == 0) 12 else hour % 12
        return String.format(Locale.KOREA, "%s %d:%02d", ampm, h, minute)
    }

    val sortKey: Int get() = hour * 60 + minute
}

/** 약 알람 목록을 휴대폰 안에 저장/불러오기 */
object MedStore {
    private const val PREFS = "meds"
    private const val KEY_LIST = "list"
    private const val KEY_NEXT_ID = "next_id"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): MutableList<Medicine> {
        val raw = prefs(ctx).getString(KEY_LIST, null) ?: return mutableListOf()
        val result = mutableListOf<Medicine>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result.add(
                    Medicine(o.getInt("id"), o.getString("name"), o.getInt("hour"), o.getInt("minute"))
                )
            }
        } catch (_: Exception) {
        }
        result.sortBy { it.sortKey }
        return result
    }

    fun save(ctx: Context, list: List<Medicine>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("hour", it.hour)
                    .put("minute", it.minute)
            )
        }
        prefs(ctx).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    fun nextId(ctx: Context): Int {
        val p = prefs(ctx)
        val id = p.getInt(KEY_NEXT_ID, 1)
        p.edit().putInt(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    fun find(ctx: Context, id: Int): Medicine? = load(ctx).firstOrNull { it.id == id }
}
