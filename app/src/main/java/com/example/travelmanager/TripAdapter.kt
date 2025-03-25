package com.example.travelmanager

import com.example.travelmanager.TripDetailsActivity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.travelmanager.databinding.ItemTripBinding

class TripAdapter(
    private val context: Context,
    private val trips: List<Trip>
) : RecyclerView.Adapter<TripAdapter.TripViewHolder>() {

    inner class TripViewHolder(private val binding: ItemTripBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(trip: Trip) {
            val displayName = if (trip.isGuest) {
                "${trip.departurePlace} (jesteś gościem)"
            } else {
                trip.departurePlace
            }

            binding.tvTripName.text = displayName
            binding.tvTripDate.text = "${trip.startDate} - ${trip.endDate}"

            binding.root.setOnClickListener {
                if (trip.tripId.isNotEmpty()) {
                    val intent = Intent(context, TripDetailsActivity::class.java)
                    intent.putExtra("tripId", trip.tripId)
                    context.startActivity(intent)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemTripBinding.inflate(inflater, parent, false)
        return TripViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        holder.bind(trips[position])
    }

    override fun getItemCount(): Int = trips.size
}

