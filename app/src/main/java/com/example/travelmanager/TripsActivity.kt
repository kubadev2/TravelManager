package com.example.travelmanager

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class TripsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var tripAdapter: TripAdapter
    private lateinit var recyclerViewTrips: RecyclerView
    private lateinit var fabAddTrip: ImageButton
    private lateinit var btnHamburger: ImageButton
    private lateinit var tvEmail: TextView
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trips)

        // Ustawiamy RecyclerView
        recyclerViewTrips = findViewById(R.id.recyclerViewTrips)
        recyclerViewTrips.layoutManager = LinearLayoutManager(this)

        // Inicjalizujemy adapter
        tripAdapter = TripAdapter(emptyList())
        recyclerViewTrips.adapter = tripAdapter

        // Pobieramy dane z Firestore
        fetchTrips()

        // Obsługa FloatingActionButton
        fabAddTrip = findViewById(R.id.fabAddTrip)
        fabAddTrip.setOnClickListener {
            Toast.makeText(this, "Dodawanie wycieczki jeszcze nie jest zaimplementowane.", Toast.LENGTH_SHORT).show()
        }

        // Obsługa hamburgera
        btnHamburger = findViewById(R.id.btnHamburger)
        tvEmail = findViewById(R.id.tvEmail)

        btnHamburger.setOnClickListener {
            // Sprawdzamy, czy użytkownik jest zalogowany
            val user = auth.currentUser
            if (user != null) {
                // Jeśli użytkownik jest zalogowany, wyświetlamy jego e-mail
                tvEmail.text = user.email
                tvEmail.visibility = if (tvEmail.visibility == View.GONE) View.VISIBLE else View.GONE
            } else {
                // Jeśli użytkownik nie jest zalogowany, wyświetlamy komunikat
                tvEmail.text = "Brak zalogowanego użytkownika"
                tvEmail.visibility = View.VISIBLE
            }
        }
    }

    private fun fetchTrips() {
        db.collection("trips")
            .get()
            .addOnSuccessListener { result ->
                val tripsList = mutableListOf<Trip>()
                for (document in result) {
                    val trip = document.toObject(Trip::class.java)
                    if (trip != null) {
                        tripsList.add(trip)
                    }
                }
                // Zaktualizuj adapter
                tripAdapter = TripAdapter(tripsList)
                recyclerViewTrips.adapter = tripAdapter
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Błąd pobierania danych: $exception", Toast.LENGTH_SHORT).show()
            }
    }
}
