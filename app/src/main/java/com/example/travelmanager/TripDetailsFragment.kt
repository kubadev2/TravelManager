package com.example.travelmanager

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.example.travelmanager.databinding.FragmentTripDetailsBinding
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class TripDetailsFragment : Fragment(R.layout.fragment_trip_details) {

    private lateinit var binding: FragmentTripDetailsBinding
    private val db = FirebaseFirestore.getInstance()
    private var tripId: String = ""

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentTripDetailsBinding.bind(view)

        tripId = arguments?.getString("tripId") ?: ""

        if (tripId.isEmpty()) {
            Toast.makeText(requireContext(), "Brak identyfikatora wycieczki", Toast.LENGTH_SHORT).show()
            return
        }

        getTripDetails(tripId)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getTripDetails(tripId: String) {
        db.collection("trips").document(tripId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val startDateStr = document.getString("startDate")
                    val endDateStr = document.getString("endDate")

                    if (!startDateStr.isNullOrEmpty() && !endDateStr.isNullOrEmpty()) {
                        refreshPlans(startDateStr, endDateStr)
                    } else {
                        Toast.makeText(requireContext(), "Brak dat wycieczki", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Nie znaleziono wycieczki", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Błąd pobierania danych: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun refreshPlans(startDateStr: String, endDateStr: String) {
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        try {
            val startDate = LocalDate.parse(startDateStr, formatter)
            val endDate = LocalDate.parse(endDateStr, formatter)

            val daysCount = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1

            if (daysCount < 1) {
                Toast.makeText(requireContext(), "Data zakończenia jest przed datą rozpoczęcia!", Toast.LENGTH_SHORT).show()
                binding.viewPager.adapter = null
                return
            }

            val adapter = PlanPagerAdapter(requireActivity(), daysCount, tripId)
            binding.viewPager.adapter = adapter

            TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
                tab.text = "Dzień ${position + 1}"
            }.attach()

        } catch (e: Exception) {
            binding.viewPager.adapter = null
            Toast.makeText(requireContext(), "Błąd parsowania daty: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun newInstance(tripId: String) = TripDetailsFragment().apply {
            arguments = Bundle().apply {
                putString("tripId", tripId)
            }
        }
    }
}
