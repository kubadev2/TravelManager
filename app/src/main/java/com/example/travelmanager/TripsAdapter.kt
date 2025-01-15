package com.example.travelmanager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.travelmanager.databinding.ItemTripBinding

class TripsAdapter : ListAdapter<Trip, TripsAdapter.TripViewHolder>(TripDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val binding = ItemTripBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TripViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        val trip = getItem(position)
        holder.bind(trip)
    }

    inner class TripViewHolder(private val binding: ItemTripBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(trip: Trip) {
            binding.trip = trip // Przypisanie obiektu trip do layoutu
            binding.executePendingBindings() // Aktualizacja widoku
        }
    }

    class TripDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<Trip>() {
        override fun areItemsTheSame(oldItem: Trip, newItem: Trip): Boolean {
            return oldItem.tripId == newItem.tripId
        }

        override fun areContentsTheSame(oldItem: Trip, newItem: Trip): Boolean {
            return oldItem == newItem
        }
    }
}
