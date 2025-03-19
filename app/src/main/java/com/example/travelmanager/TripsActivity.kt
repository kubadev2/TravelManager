package com.example.travelmanager

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TripsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var tripAdapter: TripAdapter
    private lateinit var recyclerViewTrips: RecyclerView
    private lateinit var fabAddTrip: ImageButton
    private lateinit var btnHamburger: ImageButton
    private lateinit var tvEmail: TextView
    private lateinit var btnLogout: TextView
    private lateinit var addTripForm: ConstraintLayout
    private lateinit var addTripClose: View
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private lateinit var etTripDates: EditText
    private val tripsList = mutableListOf<Trip>()

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            tvEmail.text = currentUser.email
            tvEmail.visibility = View.GONE
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
        tripAdapter = TripAdapter(this, tripsList)
        recyclerViewTrips.adapter = tripAdapter

        fetchTrips()

        addTripForm = findViewById(R.id.addTripForm)
        addTripForm.visibility = View.GONE
        addTripClose = findViewById(R.id.addTripClose)
        addTripClose.visibility = View.GONE
        addTripClose.setOnClickListener {
            addTripForm.visibility = View.GONE
            addTripClose.visibility = View.GONE
            fabAddTrip.visibility = View.VISIBLE
        }

        val btnSaveTrip = findViewById<View>(R.id.btnSaveTrip)
        btnSaveTrip.setOnClickListener {
            val departurePlace = findViewById<EditText>(R.id.etDeparturePlace).text.toString()
            val startDate = findViewById<EditText>(R.id.etTripDates).text.toString()
            val endDate = findViewById<EditText>(R.id.etTripDates2).text.toString()
            val currentUser = auth.currentUser

            if (currentUser != null && departurePlace.isNotEmpty() && startDate.isNotEmpty() && endDate.isNotEmpty()) {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                try {
                    val parsedStart = dateFormat.parse(startDate)
                    val parsedEnd = dateFormat.parse(endDate)
                    if (parsedStart != null && parsedEnd != null && parsedStart.after(parsedEnd)) {
                        Toast.makeText(this, "Data rozpoczęcia nie może być późniejsza niż data zakończenia", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Proszę wprowadzić daty w formacie dd/MM/yyyy", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val tripData = mapOf(
                    "userId" to currentUser.uid,
                    "departurePlace" to departurePlace,
                    "startDate" to startDate,
                    "endDate" to endDate
                )

                db.collection("trips")
                    .add(tripData)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Wycieczka zapisana pomyślnie", Toast.LENGTH_SHORT).show()
                        addTripForm.visibility = View.GONE
                        addTripClose.visibility = View.GONE
                        fabAddTrip.visibility = View.VISIBLE
                        fetchTrips()
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(this, "Błąd zapisu: $exception", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Proszę wypełnić wszystkie pola lub zalogować się", Toast.LENGTH_SHORT).show()
            }
        }

        fabAddTrip = findViewById(R.id.fabAddTrip)
        fabAddTrip.setOnClickListener {
            addTripForm.visibility = View.VISIBLE
            addTripClose.visibility = View.VISIBLE
            fabAddTrip.visibility = View.GONE
        }

        btnHamburger = findViewById(R.id.btnHamburger)
        tvEmail = findViewById(R.id.tvEmail)
        btnLogout = findViewById(R.id.btnLogout)
        val menuClose = findViewById<View>(R.id.menuClose)

        btnHamburger.setOnClickListener {
            if (tvEmail.visibility == View.GONE) {
                tvEmail.visibility = View.VISIBLE
                btnLogout.visibility = View.VISIBLE
                menuClose.visibility = View.VISIBLE
            } else {
                tvEmail.visibility = View.GONE
                btnLogout.visibility = View.GONE
                menuClose.visibility = View.GONE
            }
        }

        val rootLayout = findViewById<ConstraintLayout>(R.id.rootLayout)
        rootLayout.setOnClickListener {
            tvEmail.visibility = View.GONE
            btnLogout.visibility = View.GONE
            menuClose.visibility = View.GONE
        }

        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        menuClose.setOnClickListener {
            tvEmail.visibility = View.GONE
            btnLogout.visibility = View.GONE
            menuClose.visibility = View.GONE
        }

        etTripDates = findViewById(R.id.etTripDates2)
        etTripDates.setOnClickListener { showDateRangePicker() }
    }

    private fun showDateRangePicker() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        val datePicker = builder.build()
        datePicker.addOnPositiveButtonClickListener { selection ->
            val startDate = selection?.first
            val endDate = selection?.second
            val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val startFormatted = startDate?.let { format.format(Date(it)) }
            val endFormatted = endDate?.let { format.format(Date(it)) }
            findViewById<EditText>(R.id.etTripDates).setText(startFormatted)
            findViewById<EditText>(R.id.etTripDates2).setText(endFormatted)
        }
        datePicker.show(supportFragmentManager, "date_range_picker")
    }

    private fun fetchTrips() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            db.collection("trips")
                .whereEqualTo("userId", currentUser.uid)
                .get()
                .addOnSuccessListener { result ->
                    tripsList.clear()
                    for (document in result) {
                        // Pobieramy dane i kopiujemy document.id do pola tripId
                        val trip = document.toObject(Trip::class.java).copy(tripId = document.id)
                        tripsList.add(trip)
                    }
                    tripAdapter.notifyDataSetChanged()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Błąd pobierania danych: $exception", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "Zaloguj się, aby zobaczyć swoje wycieczki", Toast.LENGTH_SHORT).show()
        }
    }
}
