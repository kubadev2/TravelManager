package com.example.travelmanager

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
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

    private val PICK_PDF_REQUEST_CODE = 2001
    private var selectedPdfUri: Uri? = null

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

        // Wczytaj zapisany plan dnia (jeśli istnieje) oraz bilety przypisane do tego dnia
        loadPlanIfExists()
        loadDayTickets()

        // Po długim przytrzymaniu widoku z planem otwórz dialog edycji
        binding.tvPlanDetails.setOnLongClickListener {
            showEditPlanDialog(binding.tvPlanDetails.text.toString())
            true
        }
    }

    private fun showEditPlanDialog(currentText: String) {
        // Inflacja widoku dialogu
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_plan, null)
        val editTextPlan = dialogView.findViewById<EditText>(R.id.editTextPlan)
        val btnChooseFile = dialogView.findViewById<View>(R.id.btnChooseFile)
        editTextPlan.setText(currentText)

        // Obsługa przycisku wyboru pliku PDF w dialogu
        btnChooseFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
            }
            // Wywołanie wyboru pliku PDF
            startActivityForResult(intent, PICK_PDF_REQUEST_CODE)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Edytuj plan dnia")
            .setView(dialogView)
            .setPositiveButton("Zapisz") { dialogInterface, _ ->
                val newPlan = editTextPlan.text.toString()
                savePlan(newPlan)
                dialogInterface.dismiss()
            }
            .setNegativeButton("Anuluj") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()

        // Wymuś otwarcie klawiatury
        editTextPlan.requestFocus()
        editTextPlan.postDelayed({
            val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(editTextPlan, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    private fun savePlan(newPlan: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Brak zalogowanego użytkownika", Toast.LENGTH_SHORT).show()
            return
        }
        if (newPlan.isNotEmpty() && tripId.isNotEmpty()) {
            val planData = mapOf(
                "tripId" to tripId,
                "dayNumber" to dayNumber,
                "planDetails" to newPlan,
                "userId" to currentUser.uid
            )
            val docId = "$tripId-$dayNumber"
            db.collection("daily_plans")
                .document(docId)
                .set(planData)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Plan dnia $dayNumber zapisany!", Toast.LENGTH_SHORT).show()
                    binding.tvPlanDetails.text = newPlan
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), "Błąd zapisu planu: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(requireContext(), "Wprowadź szczegóły planu", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPlanIfExists() {
        val docId = "$tripId-$dayNumber"
        db.collection("daily_plans")
            .document(docId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val planDetails = doc.getString("planDetails") ?: ""
                    binding.tvPlanDetails.text = planDetails
                }
            }
    }

    private fun loadDayTickets() {
        db.collection("tickets")
            .whereEqualTo("tripId", tripId)
            .whereEqualTo("dayNumber", dayNumber)
            .get()
            .addOnSuccessListener { result ->
                val tickets = mutableListOf<Ticket>()
                for (document in result) {
                    val fileUrl = document.getString("fileUrl") ?: ""
                    val ticketId = document.id
                    if (fileUrl.isNotEmpty()) {
                        tickets.add(Ticket(ticketId, fileUrl, tripId, auth.currentUser?.uid ?: ""))
                    }
                }
                binding.rvDayTickets.layoutManager =
                    LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                binding.rvDayTickets.adapter = TicketAdapter(requireContext(), tickets, onClick = { fileUrl ->
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(fileUrl), "application/pdf")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    startActivity(viewIntent)
                }, onDelete = { ticket ->
                    deleteTicket(ticket)
                })
            }
    }

    private fun deleteTicket(ticket: Ticket) {
        db.collection("tickets")
            .document(ticket.ticketId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Bilet usunięty", Toast.LENGTH_SHORT).show()
                loadDayTickets()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Błąd usuwania biletu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PDF_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            selectedPdfUri = data.data
            if (selectedPdfUri != null) {
                saveTicket(selectedPdfUri!!)
            }
        }
    }

    private fun saveTicket(uri: Uri) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Brak zalogowanego użytkownika", Toast.LENGTH_SHORT).show()
            return
        }
        val ticketData = hashMapOf(
            "fileUrl" to uri.toString(),
            "tripId" to tripId,
            "dayNumber" to dayNumber,  // przypisanie biletu do konkretnego dnia
            "userId" to currentUser.uid
        )
        db.collection("tickets")
            .add(ticketData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Bilet dodany do planu dnia", Toast.LENGTH_SHORT).show()
                loadDayTickets()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Błąd zapisu biletu: ${e.message}", Toast.LENGTH_SHORT).show()
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
