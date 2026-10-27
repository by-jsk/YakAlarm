package com.yakalarm.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MedAdapter(
    private val items: List<Medicine>,
    private val onClick: (Medicine) -> Unit
) : RecyclerView.Adapter<MedAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val time: TextView = v.findViewById(R.id.timeText)
        val name: TextView = v.findViewById(R.id.nameText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_med, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val med = items[position]
        holder.time.text = med.timeText()
        holder.name.text = med.name
        holder.itemView.setOnClickListener { onClick(med) }
    }

    override fun getItemCount(): Int = items.size
}
