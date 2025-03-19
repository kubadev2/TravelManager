package com.example.travelmanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.travelmanager.databinding.FragmentPlanDayBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PlanDayFragment : Fragment() {

    private var _binding: FragmentPlanDayBinding? = null
    private val binding get() = _binding!!

    private var dayNumber: Int = 0
    private var tripId: String = ""
    private val db = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            dayNumber = it.getInt(ARG_DAY_NUMBER, 1)
            tripId = it.getString(ARG_TRIP_ID, "")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlanDayBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvDayTitle.text = "Plan dnia $dayNumber"

        // Po kliknięciu "Zapisz plan"
        binding.btnSavePlan.setOnClickListener {
            val planDetails = binding.etPlanDetails.text.toString()
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Toast.makeText(requireContext(), "Brak zalogowanego użytkownika", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (planDetails.isNotEmpty() && tripId.isNotEmpty()) {
                val planData = mapOf(
                    "tripId" to tripId,
                    "dayNumber" to dayNumber,
                    "planDetails" to planDetails,
                    "userId" to currentUser.uid
                )
                // Zapis w root-level kolekcji "daily_plans"
                val docId = "$tripId-$dayNumber" // unikalne ID np. tripId-dzien
                db.collection("daily_plans")
                    .document(docId)
                    .set(planData)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Zapisano plan dnia $dayNumber!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(requireContext(), "Błąd zapisu planu: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(requireContext(), "Wprowadź szczegóły planu", Toast.LENGTH_SHORT).show()
            }
        }

        // Możesz też wczytać plan (jeśli chcesz pokazać zapisane dane)
        loadPlanIfExists()
    }

    private fun loadPlanIfExists() {
        val docId = "$tripId-$dayNumber"
        db.collection("daily_plans")
            .document(docId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val planDetails = doc.getString("planDetails") ?: ""
                    binding.etPlanDetails.setText(planDetails)
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_DAY_NUMBER = "day_number"
        private const val ARG_TRIP_ID = "tripId"

        fun newInstance(dayNumber: Int, tripId: String) = PlanDayFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_DAY_NUMBER, dayNumber)
                putString(ARG_TRIP_ID, tripId)
            }
        }
    }
}
