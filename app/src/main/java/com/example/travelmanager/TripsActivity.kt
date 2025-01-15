package com.example.travelmanager

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.travelmanager.R
import com.example.travelmanager.Trip
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.DocumentSnapshot

class TripsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var tripsAdapter: TripsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trips)  // Upewnij się, że masz odpowiedni layout XML

        // Initialize RecyclerView
        val recyclerViewTrips = findViewById<RecyclerView>(R.id.recyclerViewTrips)
        recyclerViewTrips.layoutManager = LinearLayoutManager(this)
        tripsAdapter = TripsAdapter()
        recyclerViewTrips.adapter = tripsAdapter

        // Add button action
        val buttonAddTrip = findViewById<Button>(R.id.buttonAddTrip)  // Inicjalizacja przycisku
        buttonAddTrip.setOnClickListener {
            // Logika dodawania nowej wycieczki
            Toast.makeText(this, "Dodaj nową wycieczkę", Toast.LENGTH_SHORT).show()
        }

        // Fetch trips from Firestore
        fetchTrips()
    }

    private fun fetchTrips() {
        db.collection("trips")
            .get()
            .addOnSuccessListener { result: QuerySnapshot ->
                val tripsList = mutableListOf<Trip>()
                for (document: DocumentSnapshot in result) {
                    val trip = document.toObject(Trip::class.java)
                    if (trip != null) {
                        tripsList.add(trip)
                    }
                }
                tripsAdapter.submitList(tripsList)
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Błąd pobierania danych: $exception", Toast.LENGTH_SHORT).show()
            }
    }
}
