package com.example.travelmanager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CompanionsAdapter(
    private val companions: MutableList<Companion>,
    private val onRemoveCompanion: (Companion) -> Unit
) : RecyclerView.Adapter<CompanionsAdapter.CompanionViewHolder>() {

    inner class CompanionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvCompanionEmail: TextView = view.findViewById(R.id.tvCompanionEmail)

        init {
            view.setOnLongClickListener {
                val companion = companions[adapterPosition]
                onRemoveCompanion(companion)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompanionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_companion, parent, false)
        return CompanionViewHolder(view)
    }

    override fun onBindViewHolder(holder: CompanionViewHolder, position: Int) {
        holder.tvCompanionEmail.text = companions[position].email
    }

    override fun getItemCount(): Int = companions.size
}
