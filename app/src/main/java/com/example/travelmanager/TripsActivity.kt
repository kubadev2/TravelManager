package com.example.travelmanager

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
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
    private lateinit var btnLogout: TextView
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override fun onStart() {
        super.onStart()

        val currentUser = auth.currentUser
        if (currentUser != null) {
            tvEmail.text = currentUser.email
            tvEmail.visibility = View.GONE  // Ukryj e-mail po zalogowaniu
            btnLogout.visibility = View.GONE
        } else {
            tvEmail.text = "Brak zalogowanego użytkownika"
            tvEmail.visibility = View.VISIBLE
            btnLogout.visibility = View.GONE
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trips)

        recyclerViewTrips = findViewById(R.id.recyclerViewTrips)
        recyclerViewTrips.layoutManager = LinearLayoutManager(this)

        tripAdapter = TripAdapter(emptyList())
        recyclerViewTrips.adapter = tripAdapter

        fetchTrips()

        fabAddTrip = findViewById(R.id.fabAddTrip)
        fabAddTrip.setOnClickListener {
            Toast.makeText(this, "Dodawanie wycieczki jeszcze nie jest zaimplementowane.", Toast.LENGTH_SHORT).show()
        }

        btnHamburger = findViewById(R.id.btnHamburger)
        tvEmail = findViewById(R.id.tvEmail)
        btnLogout = findViewById(R.id.btnLogout)
        val menuClose = findViewById<View>(R.id.menuClose)  // Dodajemy odniesienie do menuClose

        // Obsługuje kliknięcie na przycisk hamburgera
        btnHamburger.setOnClickListener {
            if (tvEmail.visibility == View.GONE) {
                tvEmail.visibility = View.VISIBLE  // Pokazuje e-mail
                btnLogout.visibility = View.VISIBLE
                menuClose.visibility = View.VISIBLE  // Pokazuje menuClose
            } else {
                tvEmail.visibility = View.GONE  // Ukrywa e-mail
                btnLogout.visibility = View.GONE
                menuClose.visibility = View.GONE  // Ukrywa menuClose
            }
        }

        // Ukrywa menu, jeśli kliknięto poza nim
        val rootLayout = findViewById<ConstraintLayout>(R.id.rootLayout)
        rootLayout.setOnClickListener {
            tvEmail.visibility = View.GONE
            btnLogout.visibility = View.GONE
            menuClose.visibility = View.GONE  // Ukrywa menuClose
        }

        // Obsługuje kliknięcie na przycisk wylogowania
        btnLogout.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Ukrywa menuClose po kliknięciu w menuClose
        menuClose.setOnClickListener {
            tvEmail.visibility = View.GONE
            btnLogout.visibility = View.GONE
            menuClose.visibility = View.GONE  // Ukrywa menuClose
        }
    }


    private fun fetchTrips() {
        db.collection("trips")
            .get()
            .addOnSuccessListener { result ->
                val tripsList = mutableListOf<Trip>()
                for (document in result) {
                    val trip = document.toObject(Trip::class.java)
                    tripsList.add(trip)
                }
                tripAdapter = TripAdapter(tripsList)
                recyclerViewTrips.adapter = tripAdapter
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Błąd pobierania danych: $exception", Toast.LENGTH_SHORT).show()
            }
    }
}

