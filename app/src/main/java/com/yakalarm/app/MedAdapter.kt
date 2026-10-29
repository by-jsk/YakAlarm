package com.yakalarm.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch

class MedAdapter(
    private val items: List<Medicine>,
    private val onClick: (Medicine) -> Unit,
    private val onToggle: (Medicine, Boolean) -> Unit
) : RecyclerView.Adapter<MedAdapter.VH>() {

    init {
        setHasStableIds(true)
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val time: TextView = v.findViewById(R.id.timeText)
        val name: TextView = v.findViewById(R.id.nameText)
        val state: TextView = v.findViewById(R.id.stateText)
        val toggle: MaterialSwitch = v.findViewById(R.id.enableSwitch)
        val texts: View = v.findViewById(R.id.textBox)
    }

    override fun getItemId(position: Int): Long = items[position].id.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_med, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val med = items[position]
        holder.time.text = med.timeText()
        holder.name.text = med.name
        holder.state.text = if (med.enabled) "알람 켜짐" else "알람 꺼짐"
        holder.texts.alpha = if (med.enabled) 1f else 0.4f

        holder.toggle.setOnCheckedChangeListener(null)
        holder.toggle.isChecked = med.enabled
        holder.toggle.setOnCheckedChangeListener { _, checked ->
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onToggle(items[pos], checked)
        }
        holder.itemView.setOnClickListener { onClick(med) }
    }

    override fun getItemCount(): Int = items.size
}
