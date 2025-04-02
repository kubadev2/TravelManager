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
import com.example.travelmanager.PlanTicketAdapter
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
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

    private val PICK_FILE_REQUEST_CODE = 2001
    private var selectedFileUri: Uri? = null

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
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_plan, null)
        val editTextPlan = dialogView.findViewById<EditText>(R.id.editTextPlan)
        val btnChooseFile = dialogView.findViewById<View>(R.id.btnChooseFile)
        editTextPlan.setText(currentText)

        btnChooseFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/*"))
            }
            startActivityForResult(intent, PICK_FILE_REQUEST_CODE )

        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Edytuj plan dnia")
            .setView(dialogView)
            .setPositiveButton("Zapisz") { d, _ ->
                val newPlan = editTextPlan.text.toString()
                savePlan(newPlan)
                d.dismiss()
            }
            .setNegativeButton("Anuluj") { d, _ -> d.dismiss() }
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
                    // [CHANGE] Sprawdzamy, czy fragment wciąż istnieje
                    if (_binding == null || !isAdded) return@addOnSuccessListener

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
                if (_binding == null || !isAdded) return@addOnSuccessListener

                if (doc.exists()) {
                    val planDetails = doc.getString("planDetails") ?: ""
                    binding.tvPlanDetails.text = planDetails
                }
            }
            .addOnFailureListener {
                // obsługa błędu
            }
    }

    @OptIn(UnstableApi::class)
    private fun loadDayTickets() {
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("tickets")
            .whereEqualTo("tripId", tripId)
            .whereEqualTo("dayNumber", dayNumber)
            .whereEqualTo("userId", currentUserId)
            .get()
            .addOnSuccessListener { result ->
                if (_binding == null || !isAdded) return@addOnSuccessListener

                val tickets = mutableListOf<Ticket>()
                for (document in result) {
                    val fileUrl = document.getString("fileUrl") ?: ""
                    val ticketId = document.id
                    if (fileUrl.isNotEmpty()) {
                        tickets.add(Ticket(ticketId, fileUrl, tripId, currentUserId))
                    }
                }
                binding.rvDayTickets.layoutManager =
                    LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                binding.rvDayTickets.adapter = PlanTicketAdapter(requireContext(), tickets,
                    onClick = { fileUrl ->
                        val uri = Uri.parse(fileUrl)
                        val mimeType = requireContext().contentResolver.getType(uri)
                        if (mimeType?.startsWith("image") == true) {
                            val intent = Intent(requireContext(), FullScreenPhotosActivity::class.java).apply {
                                putStringArrayListExtra("photoList", arrayListOf(fileUrl))
                                putExtra("startIndex", 0)
                            }
                            startActivity(intent)
                        } else {
                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "application/pdf")
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            startActivity(viewIntent)
                        }
                    },
                    onDelete = { ticket -> deleteTicket(ticket) }
                )

            }
    }

    private fun deleteTicket(ticket: Ticket) {
        db.collection("tickets")
            .document(ticket.ticketId)
            .delete()
            .addOnSuccessListener {
                if (_binding == null || !isAdded) return@addOnSuccessListener

                Toast.makeText(requireContext(), "Bilet usunięty", Toast.LENGTH_SHORT).show()
                loadDayTickets()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Błąd usuwania biletu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            selectedFileUri = data.data
            if (selectedFileUri != null) {
                saveTicket(selectedFileUri!!)
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
            "dayNumber" to dayNumber,
            "userId" to currentUser.uid
        )
        db.collection("tickets")
            .add(ticketData)
            .addOnSuccessListener {
                if (_binding == null || !isAdded) return@addOnSuccessListener

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
