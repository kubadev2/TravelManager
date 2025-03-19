package com.example.travelmanager

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.travelmanager.databinding.ActivityTripDetailsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TripDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTripDetailsBinding
    private val db = FirebaseFirestore.getInstance()
    private var tripId: String = ""
    private var selectedPdfUri: Uri? = null

    private val PICK_PDF_REQUEST_CODE = 1001

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tripId = intent.getStringExtra("tripId") ?: ""
        Log.d("TripDetailsActivity", "Otrzymano tripId: $tripId")

        if (tripId.isNotEmpty()) {
            fetchTripDetails(tripId)
            fetchTicketsForTrip()
        } else {
            Toast.makeText(this, "Brak przekazanego ID wycieczki", Toast.LENGTH_SHORT).show()
        }

        // Wybór pliku PDF przy użyciu ACTION_OPEN_DOCUMENT
        binding.btnSelectPdf.setOnClickListener {
            val openDocIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
            }
            startActivityForResult(openDocIntent, PICK_PDF_REQUEST_CODE)
        }

        // Zapisanie URI pliku PDF w Firestore
        binding.btnUploadTicket.setOnClickListener {
            selectedPdfUri?.let { uri ->
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                saveTicketUrlToFirestore(uri)
            }
        }
    }

    private fun fetchTripDetails(tripId: String) {
        db.collection("trips")
            .document(tripId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val trip = document.toObject(Trip::class.java)?.copy(tripId = document.id)
                    if (trip != null) {
                        binding.tvDeparturePlace.text = trip.departurePlace
                        binding.tvStartDate.text = trip.startDate
                        binding.tvEndDate.text = trip.endDate
                    } else {
                        Toast.makeText(this, "Brak danych wycieczki", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Nie znaleziono wycieczki o tym ID", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Błąd pobierania danych: $exception", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchTicketsForTrip() {
        db.collection("tickets")
            .whereEqualTo("tripId", tripId)
            .get()
            .addOnSuccessListener { result ->
                val tickets = mutableListOf<Ticket>()
                for (document in result) {
                    val fileUrl = document.getString("fileUrl") ?: ""
                    val ticketId = document.id
                    if (fileUrl.isNotEmpty()) {
                        tickets.add(Ticket(ticketId, fileUrl))
                    }
                }
                displayTickets(tickets)
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Błąd pobierania biletów: $exception", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayTickets(tickets: List<Ticket>) {
        binding.rvTickets.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvTickets.adapter = TicketAdapter(this, tickets, onClick = { fileUrl ->
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(fileUrl), "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(viewIntent)
        }, onDelete = { ticket ->
            deleteTicket(ticket)
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PDF_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            selectedPdfUri = data.data
            binding.tvSelectedFile.text = selectedPdfUri?.lastPathSegment ?: "Plik wybrany"
            binding.btnUploadTicket.isEnabled = true
        }
    }

    private fun saveTicketUrlToFirestore(uri: Uri) {
        val ticketData = hashMapOf(
            "fileUrl" to uri.toString(),
            "tripId" to tripId,
            "userId" to FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        )

        db.collection("tickets")
            .add(ticketData)
            .addOnSuccessListener {
                Toast.makeText(this, "Ścieżka pliku została zapisana", Toast.LENGTH_SHORT).show()
                fetchTicketsForTrip() // Odśwież listę biletów po zapisaniu
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Błąd zapisywania ścieżki: $exception", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteTicket(ticket: Ticket) {
        db.collection("tickets")
            .document(ticket.ticketId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Bilet usunięty", Toast.LENGTH_SHORT).show()
                fetchTicketsForTrip()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Błąd usuwania biletu: $exception", Toast.LENGTH_SHORT).show()
            }
    }
}
