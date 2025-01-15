package com.example.travelmanager

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QueryDocumentSnapshot
import com.google.firebase.FirebaseApp

class MainActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        FirebaseApp.initializeApp(this)
        // Ładuj wycieczki, gdy aktywność jest tworzona
        loadTrips()
    }

    // Funkcja do ładowania wycieczek z Firestore
    fun loadTrips() {
        db.collection("trips").get()
            .addOnSuccessListener { result ->
                val trips = mutableListOf<Trip>()
                for (document in result) {
                    val trip = document.toObject(Trip::class.java)
                    trips.add(trip)
                }
                // Przekazanie danych do adaptera RecyclerView
                updateRecyclerView(trips)
            }
            .addOnFailureListener { exception ->
                Log.w("Trip", "Error getting documents.", exception)
            }
    }

    fun updateRecyclerView(trips: List<Trip>) {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewTrips)
        val adapter = TripsAdapter()
        recyclerView.adapter = adapter
        adapter.submitList(trips)  // Przekazywanie listy do adaptera
    }
}

